package se.tedro.maven.plugin.reproto;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.plexus.build.incremental.BuildContext;
import se.tedro.maven.plugin.reproto.gcs.GcsClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Mojo(name = "compile", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class CompileReprotoMojo extends AbstractMojo {
  public static final String DEFAULT_VERSION = "0.3";
  public static final long CACHE_TIME_MS = TimeUnit.MINUTES.toMillis(60L);

  public static final String EXECUTABLE = "reproto";
  public static final String DEFAULT_REPOSITORY = "reproto/reproto";

  private static final ObjectMapper mapper = new ObjectMapper();

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Component
  private MavenProjectHelper projectHelper;

  @Component
  private RepositorySystem repositorySystem;

  @Component
  private ResolutionErrorHandler resolutionErrorHandler;

  @Component
  private BuildContext buildContext;

  @Parameter(required = false, property = "reproto.executable")
  private String executable;

  @Parameter(required = false, property = "reproto.artifact")
  private String artifact;

  @Parameter(required = false, property = "reproto.repository")
  private String repository = DEFAULT_REPOSITORY;

  @Parameter(required = false, property = "reproto.version")
  private String version = DEFAULT_VERSION;

  @Parameter(required = false, property = "reproto.debug")
  private boolean debug = false;

  @Parameter(required = true, readonly = true, property = "localRepository")
  private ArtifactRepository localRepository;

  @Parameter(required = true, readonly = true,
      defaultValue = "${project.remoteArtifactRepositories}")
  private List<ArtifactRepository> remoteRepositories;

  /**
   * When {@code true}, skip the execution.
   */
  @Parameter(property = "reproto.skip", defaultValue = "false")
  private boolean skip;

  /**
   * A directory where native launchers for java protoc plugins will be generated.
   */
  @Parameter(defaultValue = "${project.build.directory}/reproto-plugins")
  private File pluginsDirectory;

  /**
   * Package prefix to use when generating packages.
   */
  @Parameter(required = false, property = "reproto.packagePrefix",
      defaultValue = "${project.groupId}")
  private String packagePrefix;

  /**
   * Packages to compile.
   */
  @Parameter()
  private Set<String> targets = Collections.emptySet();

  /**
   * Modules to enable.
   */
  @Parameter()
  private List<String> modules = Collections.emptyList();

  @Parameter(
      required = true,
      defaultValue = "${project.build.directory}/generated-sources/reproto/java",
      property = "reproto.output"
  )
  private File output;

  @Parameter(
      required = true,
      defaultValue = "${project.basedir}/reproto.toml",
      property = "reproto.manifest"
  )
  private File manifest;

  /**
   * Check if execution should be skipped.
   */
  private boolean isSkipped() {
    if (skip) {
      getLog().info("Skipping execution");
      return true;
    }

    if ("pom".equals(this.project.getPackaging())) {
      getLog().info("Skipping mojo execution for packaging 'pom'");
      return true;
    }

    return false;
  }

  @Override
  public void execute() throws MojoExecutionException {
    try {
      doExecute();
    } catch (final Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void doExecute() throws Exception {
    if (isSkipped()) {
      return;
    }

    final Path executable = buildExecutable();
    final Path outputDirectory = this.output.toPath();
    final Path manifest = this.manifest.toPath();
    final Reproto.Builder reproto = new Reproto.Builder(executable, outputDirectory, manifest);

    reproto.debug(debug);

    for (final String module : modules) {
      reproto.module(module);
    }

    for (final String target : targets) {
      reproto.target(target);
    }

    if (packagePrefix != null && !StringUtils.isBlank(packagePrefix)) {
      reproto.packagePrefix(packagePrefix);
    }

    reproto.build().execute(getLog());

    if (Files.isDirectory(outputDirectory)) {
      project.addCompileSourceRoot(outputDirectory.toAbsolutePath().toString());
      buildContext.refresh(outputDirectory.toFile());
    }
  }

  /**
   * Build a path to the reproto executable.
   *
   * @return an path corresponding to the executable
   */
  private Path buildExecutable() throws Exception {
    final GcsClient gcsClient = new GcsClient();

    if (this.executable != null) {
      final Path executable = Paths.get(this.executable).toAbsolutePath();

      if (!Files.isExecutable(executable)) {
        throw new IllegalArgumentException(
            "`-D reproto.executable` is not an executable: " + executable);
      }

      return executable;
    }

    final Path downloadExecutable = downloadExecutable(gcsClient);

    if (downloadExecutable != null) {
      return downloadExecutable;
    }

    return Paths.get(EXECUTABLE);
  }

  private Version getLatestVersion(final Path cacheDir, final GcsClient gcsClient) throws IOException {
    final Path path = cacheDir.resolve("version");

    final Cached cached = readCachedRelease(path);

    if (!cached.isExpired()) {
      return cached.getRelease().getVersion();
    }

    final Range req = Range.parse(version);
    final GcsClient.Release release = gcsClient.getLatestRelease(cached.getRelease(), req);

    try {
      writeLatestVersion(path, release);
    } catch (final Exception e) {
      getLog().warn("Failed to write latest version: " + path, e);
    }

    return release.getVersion();
  }

  private void writeLatestVersion(final Path path, final GcsClient.Release release) throws IOException {
    final Path parent = path.getParent();

    if (!Files.isDirectory(parent)) {
      getLog().info("Creating directory: " + parent);
      Files.createDirectories(parent);
    }

    getLog().info("Writing version (" + release + "): " + path);

    try (final OutputStream out = Files.newOutputStream(path)) {
      mapper.writeValue(out, release);
    }
  }

  private Cached readCachedRelease(final Path path) throws IOException {
    final long now = System.currentTimeMillis();

    if (!Files.isRegularFile(path)) {
      return new Cached(null, true);
    }

    final long modified = Files.getLastModifiedTime(path).to(TimeUnit.MILLISECONDS);
    final long diff = Math.max(0L, now - modified);

    final GcsClient.Release release;

    try (final InputStream in = Files.newInputStream(path)) {
      release = mapper.readValue(in, GcsClient.Release.class);
    } catch (final Exception e) {
      getLog().warn("Unable to read file (deleting): " + path, e);
      Files.delete(path);
      return new Cached(null, true);
    }

    return new Cached(release, diff >= CACHE_TIME_MS);
  }

  private Path downloadExecutable(final GcsClient gcsClient) throws Exception {
    final String userHome = System.getProperty("user.home");

    if (userHome == null || StringUtils.isBlank(userHome)) {
      throw new IllegalStateException("user.home: property not set");
    }

    final Path home = Paths.get(userHome);
    final Path cacheDir = home.resolve(".cache").resolve("reproto-maven-plugin");

    final Version version = this.getLatestVersion(cacheDir, gcsClient);

    final Path pluginsDirectory = this.pluginsDirectory.toPath();

    if (version == null) {
      return null;
    }

    if (!Files.isDirectory(pluginsDirectory)) {
      getLog().info("Creating directory: " + pluginsDirectory);
      Files.createDirectories(pluginsDirectory);
    }

    final String executableName = String.format("%s-%s", EXECUTABLE, version);
    final Path executable = pluginsDirectory.resolve(executableName);

    final String os = resolveOs();
    final String arch = resolveArch();

    if (os == null) {
      return null;
    }

    if (arch == null) {
      return null;
    }

    final String archiveName = "reproto-" + version + "-" + os + "-" + arch + ".tar.gz";

    final String archive = gcsClient.downloadUrl(archiveName);

    final Path cachedArchive = cacheDir.resolve(archiveName);

    if (!Files.isRegularFile(cachedArchive)) {
      downloadToCache(archive, cachedArchive);
    }

    // file already exists
    if (Files.isExecutable(executable)) {
      getLog().info("Using existing (cached) executable: " + executable);
      return executable;
    }

    extractArchive(cachedArchive, pluginsDirectory, executableName);

    // file already exists
    if (!Files.isExecutable(executable)) {
      throw new IllegalArgumentException(
          "Archive (" + cachedArchive + ") did not contain binary: " + executable);
    }

    return executable;
  }

  private void extractArchive(final Path source, final Path target, final String executableName)
      throws IOException, CompressorException {
    final byte[] buffer = new byte[4096];

    final CompressorStreamFactory factory = new CompressorStreamFactory();

    final InputStream in = Files.newInputStream(source);
    final InputStream gz = factory.createCompressorInputStream(CompressorStreamFactory.GZIP, in);

    try (final TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
      TarArchiveEntry entry;

      while ((entry = tar.getNextTarEntry()) != null) {
        Path path = target.resolve(entry.getName());

        if (entry.isDirectory()) {
          Files.createDirectory(path);
        } else {
          if (entry.getName().equals(EXECUTABLE)) {
            path = target.resolve(executableName);
          }

          int remaining = (int) entry.getSize();

          try (final OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE)) {
            while (remaining > 0) {
              int read = tar.read(buffer, 0, Math.min(buffer.length, remaining));

              if (read <= 0) {
                throw new IOException("failed to read file");
              }

              out.write(buffer, 0, read);
              remaining -= read;
            }
          }
        }

        Files.setPosixFilePermissions(path, convertPermissions(entry.getMode()));
        getLog().info(source + ": extracted: " + path);
      }
    }
  }

  private void downloadToCache(
      final String archive, final Path cachedArchive
  ) throws IOException {
    final byte[] buffer = new byte[4096];

    if (!Files.isDirectory(cachedArchive.getParent())) {
      Files.createDirectories(cachedArchive.getParent());
    }

    getLog().info("Downloading archive to cache: " + archive);
    final URL u = new URL(archive);

    try (final OutputStream out = Files.newOutputStream(cachedArchive,
        StandardOpenOption.CREATE_NEW)) {
      try (final InputStream in = u.openStream()) {
        while (true) {
          final int read = in.read(buffer);

          if (read <= 0) {
            break;
          }

          out.write(buffer, 0, read);
        }
      }
    }
  }

  /**
   * Convert octal permissions to a set of PosixFilePermission suitable for Java APIs.
   */
  private Set<PosixFilePermission> convertPermissions(final int mode) {
    final Set<PosixFilePermission> permissions = new HashSet<>();

    if ((mode & 0100) != 0) {
      permissions.add(PosixFilePermission.OWNER_EXECUTE);
    }

    if ((mode & 0200) != 0) {
      permissions.add(PosixFilePermission.OWNER_WRITE);
    }

    if ((mode & 0400) != 0) {
      permissions.add(PosixFilePermission.OWNER_READ);
    }

    if ((mode & 0010) != 0) {
      permissions.add(PosixFilePermission.GROUP_EXECUTE);
    }

    if ((mode & 0020) != 0) {
      permissions.add(PosixFilePermission.GROUP_WRITE);
    }

    if ((mode & 0040) != 0) {
      permissions.add(PosixFilePermission.GROUP_READ);
    }

    if ((mode & 0001) != 0) {
      permissions.add(PosixFilePermission.OTHERS_EXECUTE);
    }

    if ((mode & 0002) != 0) {
      permissions.add(PosixFilePermission.OTHERS_WRITE);
    }

    if ((mode & 0004) != 0) {
      permissions.add(PosixFilePermission.OTHERS_READ);
    }

    return permissions;
  }

  private String resolveOs() {
    final String osName = System.getProperty("os.name");

    if (osName.toLowerCase().contains("linux")) {
      return "linux";
    }

    if (osName.toLowerCase().contains("mac")) {
      return "osx";
    }

    if (osName.toLowerCase().contains("windows")) {
      return "win";
    }

    return null;
  }

  private String resolveArch() {
    final String osArch = System.getProperty("os.arch");

    if ("x86_64".equals(osArch)) {
      return "x86_64";
    }

    if ("amd64".equals(osArch)) {
      return "x86_64";
    }

    if ("x86_32".equals(osArch)) {
      return "x86_32";
    }

    return null;
  }

  private Path resolveBinaryArtifact(final Artifact artifact) throws Exception {
    final ArtifactResolutionResult result;

    final ArtifactResolutionRequest request = new ArtifactResolutionRequest()
        .setArtifact(project.getArtifact())
        .setResolveRoot(false)
        .setResolveTransitively(false)
        .setArtifactDependencies(Collections.singleton(artifact))
        .setManagedVersionMap(Collections.emptyMap())
        .setLocalRepository(localRepository)
        .setRemoteRepositories(remoteRepositories)
        .setOffline(session.isOffline())
        .setForceUpdate(session.getRequest().isUpdateSnapshots())
        .setServers(session.getRequest().getServers())
        .setMirrors(session.getRequest().getMirrors())
        .setProxies(session.getRequest().getProxies());

    result = repositorySystem.resolve(request);

    resolutionErrorHandler.throwErrors(request, result);

    final Set<Artifact> artifacts = result.getArtifacts();

    if (artifacts == null || artifacts.isEmpty()) {
      throw new RuntimeException("Unable to resolve plugin artifact");
    }

    final Artifact resolvedBinaryArtifact = artifacts.iterator().next();

    if (getLog().isDebugEnabled()) {
      getLog().debug("Resolved artifact: " + resolvedBinaryArtifact);
    }

    final Path pluginsDirectory = this.pluginsDirectory.toPath();

    final Path sourceFile = resolvedBinaryArtifact.getFile().toPath();
    final Path targetFile = pluginsDirectory.resolve(sourceFile.getFileName());

    Files.createDirectories(pluginsDirectory);
    Files.copy(sourceFile, targetFile);

    if (getLog().isDebugEnabled()) {
      getLog().debug("Executable file: " + targetFile);
    }

    return targetFile;
  }

  @Data
  private static class Cached {
    private final GcsClient.Release release;
    private final boolean expired;
  }
}

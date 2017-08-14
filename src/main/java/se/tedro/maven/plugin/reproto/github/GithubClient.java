package se.tedro.maven.plugin.reproto.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import se.tedro.maven.plugin.reproto.Version;
import se.tedro.maven.plugin.reproto.VersionReq;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GithubClient {
  public static final String API_BASE = "https://api.github.com";
  public static final String BASE = "https://github.com";

  private static final TypeReference<List<GithubRelease>> RELEASES = new TypeReference<List<GithubRelease>>() {
  };

  private final ObjectMapper mapper = new ObjectMapper();

  public Version getLatestRelease(final String repo, final VersionReq req) throws IOException {
    final String url = API_BASE + "/repos/" + repo + "/releases";

    final List<GithubRelease> githubReleases = mapper.readValue(new URL(url), RELEASES);

    final List<Version> releases = new ArrayList<>();

    for (final GithubRelease githubRelease : githubReleases) {
      final Version candidate = Version.parse(githubRelease.getTagName());

      if (!req.matches(candidate)) {
        continue;
      }

      releases.add(candidate);
    }

    releases.sort(Collections.reverseOrder(Version::compareTo));

    if (releases.isEmpty()) {
      return null;
    }

    return releases.get(0);
  }

  public String downloadUrl(final String repo, final Version version, final String file) {
    return BASE + "/" + repo + "/releases/download/" + version + "/" + file;
  }
}

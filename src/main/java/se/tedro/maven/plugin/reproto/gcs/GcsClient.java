package se.tedro.maven.plugin.reproto.gcs;

import lombok.Data;
import se.tedro.maven.plugin.reproto.Range;
import se.tedro.maven.plugin.reproto.Version;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GcsClient {
  public static final String API_BASE = "https://storage.googleapis.com/reproto-releases";

  public Release getLatestRelease(final Release known, final Range req) throws IOException {
    final String url = API_BASE + "/releases";

    final URL opened = new URL(url);

    final HttpsURLConnection connection = (HttpsURLConnection) opened.openConnection();

    if (known != null && known.getEtag() != null) {
      connection.setRequestProperty("If-None-Match", known.getEtag());
    }

    final List<Version> releases = new ArrayList<>();

    try (final InputStream is = connection.getInputStream()) {
      if (connection.getResponseCode() == 304) {
        if (known == null) {
          throw new RuntimeException("no cached release available");
        }

        return known;
      }

      final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

      while (true) {
        final String line = reader.readLine();

        if (line == null) {
          break;
        }

        final Version candidate = Version.parse(line);

        if (!req.matches(candidate)) {
          continue;
        }

        releases.add(candidate);
      }

      releases.sort(Collections.reverseOrder(Version::compareTo));

      if (releases.isEmpty()) {
        throw new RuntimeException("no remote release found");
      }

      final String etag = connection.getHeaderField("etag");

      if (etag == null) {
        throw new RuntimeException("No etag in response");
      }

      return new Release(etag, releases.get(0));
    }
  }

  public String downloadUrl(final String file) {
    return API_BASE + "/" + file;
  }

  @Data
  public static class Release {
    private final String etag;
    private final Version version;
  }
}

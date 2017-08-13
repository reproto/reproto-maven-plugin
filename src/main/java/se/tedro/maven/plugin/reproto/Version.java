package se.tedro.maven.plugin.reproto;

import lombok.Data;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class Version implements Comparable<Version> {
  private final List<Integer> parts;

  public static Version parse(final String version) {
    final List<Integer> parts = Arrays.stream(version.split("\\.")).map(Integer::parseUnsignedInt).collect(Collectors.toList());
    return new Version(parts);
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder();

    for (int part : parts) {
      if (builder.length() > 0) {
        builder.append('.');
      }

      builder.append(Integer.toString(part));
    }

    return builder.toString();
  }

  @Override
  public int compareTo(final Version o) {
    final Iterator<Integer> a = parts.iterator();
    final Iterator<Integer> b = o.getParts().iterator();

    while (a.hasNext()) {
      if (!b.hasNext()) {
        return 1;
      }

      final int c = Integer.compare(a.next(), b.next());

      if (c != 0) {
        return c;
      }
    }

    if (b.hasNext()) {
      return -1;
    }

    return 0;
  }
}
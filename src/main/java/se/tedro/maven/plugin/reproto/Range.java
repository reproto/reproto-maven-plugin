package se.tedro.maven.plugin.reproto;

import com.google.common.collect.ImmutableList;
import lombok.Data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class Range {
  private final List<Integer> parts;

  public static Range parse(final String version) {
    final List<Integer> parts = new ArrayList<>();

    for (final String part : version.trim().split("\\.")) {
      parts.add(Integer.parseUnsignedInt(part));
    }

    return new Range(parts);
  }

  public boolean matches(final Version version) {
    final Iterator<Integer> req = parts.iterator();
    final Iterator<Integer> current = version.getParts().iterator();

    while (req.hasNext()) {
      final Integer r = req.next();

      if (!current.hasNext()) {
        return false;
      }

      if (!r.equals(current.next())) {
        return false;
      }
    }

    return true;
  }

  public static Range create(int a) {
    return new Range(ImmutableList.of(a));
  }

  public static Range create(int a, int b) {
    return new Range(ImmutableList.of(a, b));
  }

  public static Range create(int a, int b, int c) {
    return new Range(ImmutableList.of(a, b, c));
  }
}

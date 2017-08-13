package se.tedro.mavne.plugin.reproto;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import se.tedro.maven.plugin.reproto.Version;
import se.tedro.maven.plugin.reproto.VersionReq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class VersionTest {
  @Test
  public void testVersion() {
    assertTrue(VersionReq.create(0, 1).matches(Version.parse("0.1.99")));
    assertFalse(VersionReq.create(0, 1).matches(Version.parse("0.2.99")));
  }

  @Test
  public void testSorting() {
    final List<Version> versions = new ArrayList<>();

    final Version a = Version.parse("0.0.1");
    final Version b = Version.parse("0.1.1");
    final Version c = Version.parse("0.1");
    final Version d = Version.parse("0.0.2");
    final Version e = Version.parse("0.1.0");

    versions.add(a);
    versions.add(b);
    versions.add(c);
    versions.add(d);
    versions.add(e);

    Collections.sort(versions);
    Collections.reverse(versions);

    assertEquals(ImmutableList.of(b, e, c, d, a), versions);
  }
}

package se.tedro.maven.plugin.reproto;

import com.google.common.collect.ImmutableList;
import lombok.Data;

import java.util.Iterator;
import java.util.List;

@Data
public class VersionReq {
    private final List<Integer> parts;

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

    public static VersionReq create(int a) {
        return new VersionReq(ImmutableList.of(a));
    }

    public static VersionReq create(int a, int b) {
        return new VersionReq(ImmutableList.of(a, b));
    }

    public static VersionReq create(int a, int b, int c) {
        return new VersionReq(ImmutableList.of(a, b, c));
    }
}

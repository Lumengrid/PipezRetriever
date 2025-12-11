package net.minecraft.world.flag;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nullable;

public final class FeatureFlagSet {
    private static final FeatureFlagSet EMPTY = new FeatureFlagSet(null, 0L);
    public static final int MAX_CONTAINER_SIZE = 64;
    @Nullable
    private final FeatureFlagUniverse universe;
    private final long mask;

    private FeatureFlagSet(@Nullable FeatureFlagUniverse universe, long mask) {
        this.universe = universe;
        this.mask = mask;
    }

    static FeatureFlagSet create(FeatureFlagUniverse universe, Collection<FeatureFlag> flags) {
        if (flags.isEmpty()) {
            return EMPTY;
        } else {
            long i = computeMask(universe, 0L, flags);
            return new FeatureFlagSet(universe, i);
        }
    }

    public static FeatureFlagSet of() {
        return EMPTY;
    }

    public static FeatureFlagSet of(FeatureFlag flag) {
        return new FeatureFlagSet(flag.universe, flag.mask);
    }

    public static FeatureFlagSet of(FeatureFlag flag, FeatureFlag... others) {
        long i = others.length == 0 ? flag.mask : computeMask(flag.universe, flag.mask, Arrays.asList(others));
        return new FeatureFlagSet(flag.universe, i);
    }

    private static long computeMask(FeatureFlagUniverse universe, long mask, Iterable<FeatureFlag> flags) {
        for (FeatureFlag featureflag : flags) {
            if (universe != featureflag.universe) {
                throw new IllegalStateException("Mismatched feature universe, expected '" + universe + "', but got '" + featureflag.universe + "'");
            }

            mask |= featureflag.mask;
        }

        return mask;
    }

    public boolean contains(FeatureFlag flag) {
        return this.universe != flag.universe ? false : (this.mask & flag.mask) != 0L;
    }

    public boolean isEmpty() {
        return this.equals(EMPTY);
    }

    public boolean isSubsetOf(FeatureFlagSet set) {
        if (this.universe == null) {
            return true;
        } else {
            return this.universe != set.universe ? false : (this.mask & ~set.mask) == 0L;
        }
    }

    public boolean intersects(FeatureFlagSet set) {
        return this.universe != null && set.universe != null && this.universe == set.universe ? (this.mask & set.mask) != 0L : false;
    }

    public FeatureFlagSet join(FeatureFlagSet other) {
        if (this.universe == null) {
            return other;
        } else if (other.universe == null) {
            return this;
        } else if (this.universe != other.universe) {
            throw new IllegalArgumentException("Mismatched set elements: '" + this.universe + "' != '" + other.universe + "'");
        } else {
            return new FeatureFlagSet(this.universe, this.mask | other.mask);
        }
    }

    public FeatureFlagSet subtract(FeatureFlagSet other) {
        if (this.universe == null || other.universe == null) {
            return this;
        } else if (this.universe != other.universe) {
            throw new IllegalArgumentException("Mismatched set elements: '" + this.universe + "' != '" + other.universe + "'");
        } else {
            long i = this.mask & ~other.mask;
            return i == 0L ? EMPTY : new FeatureFlagSet(this.universe, i);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            if (other instanceof FeatureFlagSet featureflagset && this.universe == featureflagset.universe && this.mask == featureflagset.mask) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return (int)HashCommon.mix(this.mask);
    }
}

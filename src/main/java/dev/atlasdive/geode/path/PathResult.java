package dev.atlasdive.geode.path;
import java.util.Collections;
import java.util.List;

public final class PathResult {
    public final List<PathStep> steps;
    public final int expanded;
    public PathResult(List<PathStep> steps, int expanded) {
        this.steps=Collections.unmodifiableList(steps);
        this.expanded=expanded;
    }
    public boolean found() { return !steps.isEmpty(); }
}

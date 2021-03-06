package tesseract.graph;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import tesseract.util.Node;
import tesseract.util.Pos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * The Path is a class that should work with paths for grids.
 */
public final class Path<C extends IConnectable> {

    private final Pos origin;
    private final Pos target;
    private final Long2ObjectMap<C> full = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectMap<C> cross = new Long2ObjectLinkedOpenHashMap<>();

    /**
     * Creates a path instance.
     *
     * @param connectors The connectors array.
     * @param path The path queue.
     */
    protected Path(@Nonnull Long2ObjectMap<Connectivity.Cache<C>> connectors, @Nonnull ArrayDeque<Node> path) {
        origin = path.pollLast();
        target = path.pollFirst();

        Iterator<Node> iterator = path.descendingIterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            long pos = node.asLong();

            C cable = connectors.get(pos).value();

            full.put(pos, cable);
            if (node.isCrossroad()) {
                cross.put(pos, cable);
            }
        }
    }

    /**
     * @return Gets the origin position.
     */
    @Nullable
    public Pos origin() {
        return origin;
    }

    /**
     * @return Gets the target position.
     */
    @Nullable
    public Pos target() {
        return target;
    }

    /**
     * @return Gets the full connectors path.
     */
    @Nonnull
    public Long2ObjectMap<C> getFull() {
        return full;
    }

    /**
     * @return Gets the crossroad connectors path.
     */
    @Nonnull
    public Long2ObjectMap<C> getCross() {
        return cross;
    }

    /**
     * @return Checks that the path is empty.
     */
    public boolean isEmpty() {
        return (origin == null || target == null);
    }
}
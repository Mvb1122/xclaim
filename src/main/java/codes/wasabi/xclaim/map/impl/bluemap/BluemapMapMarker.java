package codes.wasabi.xclaim.map.impl.bluemap;

import codes.wasabi.xclaim.XClaim;
import codes.wasabi.xclaim.api.Claim;
import codes.wasabi.xclaim.map.MapMarker;
import codes.wasabi.xclaim.map.util.ChunkBitmap;
import codes.wasabi.xclaim.map.util.ClaimUtil;
import codes.wasabi.xclaim.map.util.Point;
import codes.wasabi.xclaim.platform.Platform;
import codes.wasabi.xclaim.util.BoundingBox;
import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;

public class BluemapMapMarker implements MapMarker {

    private final MarkerSet set;
    private final ExtrudeMarker marker;

    public BluemapMapMarker(MarkerSet set, ExtrudeMarker marker) {
        this.set = set;
        this.marker = marker;
    }

    public ExtrudeMarker get() {
        return marker;
    }

    private Shape buildShapeFromPoints(List<Point> points) {
        Shape.Builder shapeBuilder = Shape.builder();
        for (Point p : points) {
            shapeBuilder.addPoint(Vector2d.from(p.x(), p.y()));
        }
        return shapeBuilder.build();
    }

    @Override
    public void update(@NotNull Claim claim) {
        if (XClaim.mainConfig.getBoolean("mapping-integration.debug"))
            XClaim.logger.log(Level.INFO, "Updating claim " + claim.getName() + " on map!");

        ChunkBitmap bmp = new ChunkBitmap(claim.getChunks());
        List<List<Point>> edges = bmp.traceBlocks(true);

        if (edges.size() < 1) return;

        this.marker.setShape(
                this.buildShapeFromPoints(edges.get(0)),
                this.marker.getShapeMinY(),
                this.marker.getShapeMaxY()
        );

        Collection<Shape> holes = this.marker.getHoles();
        holes.clear();
        for (int i=1; i < edges.size(); i++) {
            holes.add(this.buildShapeFromPoints(edges.get(i)));
        }
    }

    @Override
    public void deleteMarker() {
        (new HashSet<>(set.getMarkers().entrySet())).stream()
                .filter((Map.Entry<String, Marker> entry) -> Objects.equals(entry.getValue(), this.marker))
                .map(Map.Entry::getKey)
                .forEach(set::remove);
    }

    // Package Private
    private static final Map<String, MarkerSet> markerSetMap = new HashMap<>();
    private static @Nullable MarkerSet getMarkerSet(BlueMapAPI api, Claim claim) {
        World world = claim.getWorld();
        if (world == null) return null;

        // Check if we've already created a marker set and return that if needed.
        String MapLocation = claim.getOwner().getName() + "'s claims"; // Index them by the set name, which includes the player name.
        if (markerSetMap.containsKey(MapLocation)) return markerSetMap.get(MapLocation);

        Optional<BlueMapWorld> opt = api.getWorld(world);
        if (!opt.isPresent()) return null;
        BlueMapWorld bmw = opt.get();

        MarkerSet ms = MarkerSet.builder()
                // V2: Bump dynmap-marker-name to mapping-marker-name
                .label(MapLocation)
                .build();

        // Save the set for later disposal.
        markerSetMap.put(MapLocation, ms);

        for (BlueMapMap map : bmw.getMaps()) {
            // Put our marker on the map.
            map.getMarkerSets().put(MapLocation, ms);
        }

        return ms;
    }

    static @Nullable BluemapMapMarker getMarker(Object apiInstance, Claim claim) {
        BlueMapAPI api = (BlueMapAPI) apiInstance;
        MarkerSet ms = getMarkerSet(api, claim);
        if (ms == null) return null;

        String token = claim.getUniqueToken();
        Marker existing = ms.get(token);
        if (existing != null) {
            if (existing instanceof ExtrudeMarker) {
                BluemapMapMarker marker = new BluemapMapMarker(ms, (ExtrudeMarker) existing);
                return marker;
            } else {
                ms.remove(token);
            }
        }

        BoundingBox bounds = claim.getOuterBounds();
        Vector mins = bounds.getMins();
        Vector maxs = bounds.getMaxs();

        World world = claim.getWorld();
        if (world == null) {
            world = Bukkit.getWorlds().stream().findFirst().orElseThrow(null);
        }

        java.awt.Color col = ClaimUtil.getClaimColor(claim);

        ExtrudeMarker marker = ExtrudeMarker.builder()
                .shape(
                        Shape.createRect(
                                mins.getX(), mins.getZ(),
                                maxs.getX(), maxs.getZ()
                        ),
                        Platform.get().getWorldMinHeight(world),
                        world.getMaxHeight()
                )
                .label(claim.getName())
                .fillColor(new Color(
                        col.getRed(),
                        col.getGreen(),
                        col.getBlue(),
                        0.2f
                ))
                .lineColor(new Color(
                        col.getRed(),
                        col.getGreen(),
                        col.getBlue(),
                        0.4f
                ))
                .build();

        ms.put(token, marker);

        return new BluemapMapMarker(ms, marker);
    }

    static void cleanup(Object apiInstance) {
        return;
        /* You don't actually have to remove them, BlueMap won't persist them anyway.
        BlueMapAPI api = (BlueMapAPI) apiInstance;
        for (BlueMapMap map : api.getMaps()) {
            map.getMarkerSets().remove(markerSetId);
        }
        */
    }

}

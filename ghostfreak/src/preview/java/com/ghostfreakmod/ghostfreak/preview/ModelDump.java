package com.ghostfreakmod.ghostfreak.preview;

import com.ghostfreakmod.ghostfreak.client.GhostfreakModel;
import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Dev tool: bakes the real GhostfreakModel and writes every quad (position, uv, normal) for several
 * poses to JSON, so tools/*.py can paint the texture from 3D positions and render previews
 * without launching Minecraft. Run with: ./gradlew dumpModel
 */
public class ModelDump {
    private static final int TEX_W = GhostfreakModel.TEX_W;
    private static final int TEX_H = GhostfreakModel.TEX_H;

    /** Collects four vertices per quad, exactly as ModelPart emits them. */
    private static class Collector implements VertexConsumer {
        final List<float[]> quads = new ArrayList<>();
        private final float[] current = new float[32];
        private int count;
        private float x, y, z, u, v, nx, ny, nz;

        @Override public VertexConsumer vertex(double x, double y, double z) { this.x = (float) x; this.y = (float) y; this.z = (float) z; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { this.nx = x; this.ny = y; this.nz = z; return this; }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }

        @Override
        public void endVertex() {
            float[] vertex = {x * 16.0F, y * 16.0F, z * 16.0F, u * TEX_W, v * TEX_H, nx, ny, nz};
            System.arraycopy(vertex, 0, current, count * 8, 8);
            if (++count == 4) {
                quads.add(current.clone());
                count = 0;
            }
        }
    }

    private static String dump(ModelPart root) {
        Collector collector = new Collector();
        root.render(new PoseStack(), collector, 0, 0, 1.0F, 1.0F, 1.0F, 1.0F);
        StringBuilder sb = new StringBuilder("[");
        for (int q = 0; q < collector.quads.size(); q++) {
            if (q > 0) sb.append(',');
            sb.append('[');
            float[] data = collector.quads.get(q);
            for (int i = 0; i < data.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(String.format(Locale.ROOT, "%.4f", data[i]));
            }
            sb.append(']');
        }
        return sb.append(']').toString();
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out.getParent());

        // name -> {t, move, headYaw, headPitch, reveal, strike, dance, sit, phase}
        Map<String, float[]> poses = new LinkedHashMap<>();
        poses.put("idle", new float[]{20, 0.0F, 0, 0, 0, 0, 0, 0, 0});
        poses.put("fly", new float[]{44, 0.9F, 0, 0, 0, 0, 0, 0, 0});
        poses.put("reveal", new float[]{30, 0.0F, 0, 0, 1, 0, 0, 0, 0});
        poses.put("strike", new float[]{30, 0.4F, 0, 0, 1, 1, 0, 0, 0});
        poses.put("dance", new float[]{26, 0.0F, 0, 0, 1, 0, 1, 0, 0});
        poses.put("dance2", new float[]{33, 0.0F, 0, 0, 1, 0, 1, 0, 0});
        poses.put("sit", new float[]{20, 0.0F, 0, 0, 0, 0, 0, 1, 0});
        poses.put("look", new float[]{70, 0.0F, 35, -15, 0, 0, 0, 0, 0});

        // Painter hints, in model-space pixels (y points down). eye: x0, x1, y0, y1 and the z of the
        // face it sits on. neckY: bottom of the head. crack: top and bottom of the body and the half
        // width of the torso. clawY / clawX: where the hand planes start and how far out they are.
        // tentacleV: texels from this row down belong to the tentacles. tentacleY: where they hang from.
        StringBuilder json = new StringBuilder("{\"texW\":" + TEX_W + ",\"texH\":" + TEX_H
                + ",\"eye\":[1,3,-11,-8,-3],\"neckY\":-5,\"crack\":[-5,24,4],\"clawY\":11,\"clawX\":3.5"
                + ",\"tentacleV\":" + GhostfreakModel.TENTACLE_V + ",\"tentacleY\":-3,\"poses\":{");
        ModelPart root = GhostfreakModel.createBodyLayer().bakeRoot();
        GhostfreakModel<GhostfreakEntity> model = new GhostfreakModel<>(root);
        json.append("\"rest\":").append(dump(root));
        for (Map.Entry<String, float[]> pose : poses.entrySet()) {
            float[] p = pose.getValue();
            model.applyPose(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8]);
            json.append(",\"").append(pose.getKey()).append("\":").append(dump(root));
        }
        json.append("}}");
        Files.writeString(out, json.toString());
        System.out.println("Wrote " + out);
    }
}

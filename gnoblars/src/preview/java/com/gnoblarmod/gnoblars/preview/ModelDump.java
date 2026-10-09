package com.gnoblarmod.gnoblars.preview;

import com.gnoblarmod.gnoblars.client.GnoblarModel;
import com.gnoblarmod.gnoblars.entity.GnoblarEntity;
import com.gnoblarmod.gnoblars.entity.GnoblarVariant;
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
 * Dev tool: bakes the real GnoblarModel and writes every quad (position, uv, normal) for several
 * poses to JSON, so tools/*.py can paint the texture from 3D positions and render previews
 * without launching Minecraft. Run with: ./gradlew dumpModel
 */
public class ModelDump {
    private static final int TEX_SIZE = 64;

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
            float[] vertex = {x * 16.0F, y * 16.0F, z * 16.0F, u * TEX_SIZE, v * TEX_SIZE, nx, ny, nz};
            System.arraycopy(vertex, 0, current, count * 8, 8);
            if (++count == 4) {
                quads.add(current.clone());
                count = 0;
            }
        }
    }

    private static String dump(GnoblarModel<GnoblarEntity> model, ModelPart root) {
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

        // name -> {limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, sit, sniff, scared}
        Map<String, float[]> poses = new LinkedHashMap<>();
        poses.put("idle", new float[]{0, 0.0F, 20, 0, 0, 0, 0, 0});
        poses.put("walk", new float[]{1.6F, 0.9F, 40, 0, 0, 0, 0, 0});
        poses.put("scared", new float[]{2.4F, 1.0F, 30, 0, 0, 0, 0, 1});
        poses.put("sit", new float[]{0, 0.0F, 20, 0, 0, 1, 0, 0});
        poses.put("sniff", new float[]{0, 0.0F, 12, 0, 0, 0, 1, 0});
        // extra phases, so tools/check_clipping.py sees the whole range of each animation
        for (int i = 0; i < 8; i++) {
            poses.put("walk_" + i, new float[]{i * 0.8F, 1.0F, 10 + i * 5, 0, 0, 0, 0, 0});
            poses.put("walk_look_" + i, new float[]{i * 0.8F, 1.0F, 10 + i * 5, (i - 4) * 12F, (i - 4) * 8F, 0, 0, 0});
            poses.put("scared_" + i, new float[]{i * 0.8F, 1.0F, i * 3.5F, 0, 0, 0, 0, 1});
            poses.put("sniff_" + i, new float[]{0, 0.0F, i * 2.2F, 0, 0, 0, 1, 0});
            poses.put("sit_look_" + i, new float[]{0, 0.0F, 20, (i - 4) * 15F, (i - 4) * 10F, 1, 0, 0});
        }

        StringBuilder json = new StringBuilder("{\"texSize\":" + TEX_SIZE + ",\"poses\":{");
        ModelPart root = GnoblarModel.createBodyLayer().bakeRoot();
        GnoblarModel<GnoblarEntity> model = new GnoblarModel<>(root);
        model.setWartSpot(null);   // the painter needs every wart cube (all variants) in the rest geometry
        json.append("\"rest\":").append(dump(model, root));
        for (Map.Entry<String, float[]> pose : poses.entrySet()) {
            float[] p = pose.getValue();
            model.applyPose(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]);
            json.append(",\"").append(pose.getKey()).append("\":").append(dump(model, root));
        }
        json.append("},\"variants\":{");
        // each variant as the game shows it: idle, with only its own wart cube visible
        boolean first = true;
        for (GnoblarVariant variant : GnoblarVariant.values()) {
            model.setWartSpot(variant.wart());
            model.applyPose(0, 0.0F, 20, 0, 0, 0, 0, 0);
            json.append(first ? "" : ",").append("\"").append(variant.id()).append("\":").append(dump(model, root));
            first = false;
        }
        json.append("}}");
        Files.writeString(out, json.toString());
        System.out.println("Wrote " + out);
    }
}

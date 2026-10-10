package com.aceliada.preview;

import com.aceliada.client.AcelaModelLayer;
import com.aceliada.entity.AcelaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Dev tool: bakes the real Acela model (vanilla player mesh plus the pipe) and writes every quad
 * (position, uv, normal) for a few poses to JSON, so tools/render_preview.py can draw it with the skin.
 * Run with: ./gradlew dumpModel
 */
public class ModelDump {
    private static final int TEX_SIZE = 64;

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

    private static String dump(PlayerModel<AcelaEntity> model) {
        Collector collector = new Collector();
        model.renderToBuffer(new PoseStack(), collector, 0, 0, 1.0F, 1.0F, 1.0F, 1.0F);
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

    /** Copies the rotations of the base parts onto the overlay parts, as PlayerModel.setupAnim does. */
    private static void syncOverlays(PlayerModel<AcelaEntity> m) {
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out.getParent());
        ModelPart root = AcelaModelLayer.create().bakeRoot();
        PlayerModel<AcelaEntity> model = new PlayerModel<>(root, false);
        model.young = false; // EntityModel defaults to true; the renderer sets it per entity

        StringBuilder json = new StringBuilder("{\"texSize\":" + TEX_SIZE + ",\"poses\":{");
        syncOverlays(model);
        json.append("\"idle\":").append(dump(model));

        // Mid-stride, head turned a little: the vanilla walk swing.
        model.rightArm.xRot = 0.7F;
        model.leftArm.xRot = -0.7F;
        model.rightLeg.xRot = -0.7F;
        model.leftLeg.xRot = 0.7F;
        model.head.yRot = 0.4F;
        syncOverlays(model);
        json.append(",\"walk\":").append(dump(model));

        // Swinging at someone with the right arm.
        model.rightArm.xRot = -2.0F;
        model.rightArm.yRot = 0.3F;
        model.leftArm.xRot = 0.2F;
        model.rightLeg.xRot = 0.0F;
        model.leftLeg.xRot = 0.0F;
        model.head.yRot = 0.0F;
        model.head.xRot = 0.2F;
        syncOverlays(model);
        json.append(",\"attack\":").append(dump(model));

        json.append("}}");
        Files.writeString(out, json.toString());
        System.out.println("Wrote " + out);
    }
}

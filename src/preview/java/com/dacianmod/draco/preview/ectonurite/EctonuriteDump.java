package com.dacianmod.draco.preview.ectonurite;

import com.dacianmod.draco.preview.ModelDump.Collector;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * Dev tool: bakes the three candidate Ectonurite models and writes their geometry, in several
 * poses, to build/preview/ectonurite_{a,b,c}.json for tools/paint_ectonurite.py and
 * tools/render_ectonurite.py. Run with: ./gradlew dumpEctonurite
 */
public class EctonuriteDump {
    /** Painter hints in model-space pixels (y points down). See paint_ectonurite.py. */
    private record Variant(String id, int texW, int texH, LayerDefinition layer,
                           Function<ModelPart, EctonuriteBase> factory, String meta) { }

    private static String dump(ModelPart root, int texW, int texH) {
        Collector collector = new Collector(texW, texH);
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
        Path outDir = Path.of(args[0]);
        Files.createDirectories(outDir);

        // eye: x0, x1, y0, y1 and the z of the face it sits on. neckY: bottom of the head.
        // crack: top and bottom of the body and the half width of the torso. clawY / clawX: where the
        // hand planes start and how far out from the centre line they are.
        Variant[] variants = {
                new Variant("a", 64, 64, EctonuriteModelA.createBodyLayer(), r -> new EctonuriteModelA(r),
                        "\"eye\":[1,3,-9,-6,-3],\"neckY\":-5,\"crack\":[-5,24,4],\"clawY\":11,\"clawX\":3.5"),
                new Variant("b", 64, 64, EctonuriteModelB.createBodyLayer(), r -> new EctonuriteModelB(r),
                        "\"eye\":[1,4,4,6,-4],\"neckY\":9,\"crack\":[9,25,3],\"clawY\":16,\"clawX\":3.5"),
                new Variant("c", 128, 64, EctonuriteModelC.createBodyLayer(), r -> new EctonuriteModelC(r),
                        "\"eye\":[0,3,-8,-6,-4],\"neckY\":-4,\"crack\":[-4,44,4],\"clawY\":17,\"clawX\":3.5"),
        };

        // name -> {ageInTicks, moveAmount, headYaw, headPitch}
        Map<String, float[]> poses = new LinkedHashMap<>();
        poses.put("idle", new float[]{20, 0.0F, 0, 0});
        poses.put("drift", new float[]{44, 0.9F, 0, 0});
        poses.put("look", new float[]{70, 0.0F, 35, -15});

        for (Variant v : variants) {
            ModelPart root = v.layer().bakeRoot();
            EctonuriteBase model = v.factory().apply(root);
            StringBuilder json = new StringBuilder("{\"texW\":" + v.texW() + ",\"texH\":" + v.texH() + "," + v.meta());
            json.append(",\"poses\":{\"rest\":").append(dump(root, v.texW(), v.texH()));
            for (Map.Entry<String, float[]> pose : poses.entrySet()) {
                float[] p = pose.getValue();
                model.applyPose(p[0], p[1], p[2], p[3]);
                json.append(",\"").append(pose.getKey()).append("\":").append(dump(root, v.texW(), v.texH()));
            }
            json.append("}}");
            Path out = outDir.resolve("ectonurite_" + v.id() + ".json");
            Files.writeString(out, json.toString());
            System.out.println("Wrote " + out);
        }
    }
}

package com.pixelclouds.cloud;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudFieldTest {
    private static CloudField.View view() {
        CloudField.View v = new CloudField.View();
        v.x = 0;
        v.y = 70;
        v.z = 0;
        return v;
    }

    @Test
    void generatedCloudsFitTheAtlasSlotAndUseEveryTone() {
        Random rnd = new Random(1);
        for (int i = 0; i < 200; i++) {
            int width = 24 + rnd.nextInt(90);
            List<Blob> blobs = CloudShapes.cumulus(rnd, width, rnd.nextDouble(), true);
            CloudImage img = CloudRaster.rasterise(blobs, 126, 62);
            assertTrue(img.width <= 126 && img.height <= 62, "image " + img.width + "x" + img.height);
            assertTrue(img.filledTexels() > 0);
            boolean[] seen = new boolean[CloudImage.TONES];
            for (byte t : img.tones) {
                seen[t] = true;
            }
            for (int t = CloudImage.DEEP; t <= CloudImage.LIGHT; t++) {
                assertTrue(seen[t], "tone " + t + " missing from cloud " + i);
            }
        }
    }

    @Test
    void fillingCoversTheSkyWithinTheRadius() {
        CloudField.Settings s = new CloudField.Settings();
        CloudField field = new CloudField(s, 5);
        field.tick(view());
        int target = field.targetCount(0);
        assertTrue(field.clouds().size() >= target * 0.9, field.clouds().size() + " < " + target);
        for (Cloud c : field.clouds()) {
            assertTrue(Math.hypot(c.x, c.z) <= s.radius * 1.05);
        }
    }

    @Test
    void stormsBringMoreCover() {
        CloudField field = new CloudField(new CloudField.Settings(), 5);
        double clearCover = field.targetCount(0) * 55.0 * 55.0;
        double stormCover = field.targetCount(1) * 80.0 * 80.0;
        assertTrue(stormCover > clearCover * 3);
    }

    @Test
    void aCloudPullsApartIntoTwo() {
        CloudField.Settings s = new CloudField.Settings();
        s.manageCount = false;
        CloudField field = new CloudField(s, 3);
        List<Blob> blobs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            blobs.add(new Blob(-18 + i * 4, -4, 5, 5));
            blobs.add(new Blob(6 + i * 4, -4, 5, 5));
        }
        Cloud c = field.addForTest(0, 180, 0, blobs);
        field.startSplit(c);
        for (int t = 0; t < 1500 && field.clouds().size() < 2; t++) {
            field.tick(view());
        }
        assertEquals(2, field.clouds().size());
        int total = 0;
        for (Cloud piece : field.clouds()) {
            total += piece.blobs().size();
            assertTrue(!piece.isSplitting());
        }
        assertEquals(8, total);
        // The pieces are laid out along the camera's right, which is +X for this view.
        Cloud a = field.clouds().get(0), b = field.clouds().get(1);
        assertTrue(Math.abs(a.x - b.x) > 20, "pieces at " + a.x + " and " + b.x);
        assertEquals(a.z, b.z, 1e-6);
    }

    @Test
    void overlappingCloudsMerge() {
        CloudField.Settings s = new CloudField.Settings();
        s.manageCount = false;
        CloudField field = new CloudField(s, 4);
        Random rnd = new Random(9);
        field.addForTest(0, 180, 0, CloudShapes.cumulus(rnd, 30, 0.2, true));
        field.addForTest(20, 181, 4, CloudShapes.cumulus(rnd, 30, 0.2, true));
        int before = 0;
        for (Cloud c : field.clouds()) {
            before += c.blobs().size();
        }
        for (int t = 0; t < 400 && field.clouds().size() > 1; t++) {
            field.tick(view());
        }
        assertEquals(1, field.clouds().size());
        assertEquals(before, field.clouds().get(0).blobs().size());
    }

    @Test
    void cloudsFarApartInDepthDoNotMerge() {
        CloudField.Settings s = new CloudField.Settings();
        s.manageCount = false;
        CloudField field = new CloudField(s, 4);
        Random rnd = new Random(9);
        // Same screen position (camera looks along +Z), 200 blocks apart in depth.
        field.addForTest(0, 180, 100, CloudShapes.cumulus(rnd, 30, 0.2, true));
        field.addForTest(0, 180, 300, CloudShapes.cumulus(rnd, 30, 0.2, true));
        for (int t = 0; t < 400; t++) {
            field.tick(view());
        }
        assertEquals(2, field.clouds().size());
    }

    @Test
    void palettesFollowTheSky() {
        int[] day = CloudPalette.compute(1, 0, 0, 0);
        int[] dusk = CloudPalette.compute(0.05, 0, 0, 0);
        int[] storm = CloudPalette.compute(1, 1, 1, 0);
        int[] night = CloudPalette.compute(-1, 0, 0, 0);
        assertEquals(0xFFFFFF, day[CloudImage.LIGHT]);
        int body = dusk[CloudImage.BODY];
        assertTrue(((body >> 16) & 255) > (body & 255) + 40, "dusk should be warm");
        int grey = storm[CloudImage.BODY];
        assertTrue(Math.abs(((grey >> 16) & 255) - (grey & 255)) < 20, "storm should be grey");
        assertTrue((night[CloudImage.LIGHT] & 255) < 0x90, "night should be dark");
    }
}

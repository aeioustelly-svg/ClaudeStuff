package com.aceliada.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The vanilla player mesh (wide arms, 64x64 skin layout) with a pipe in the left corner of the mouth.
 * The pipe uses the unused skin area at u 56-64, v 16-25.
 */
public final class AcelaModelLayer {
    private AcelaModelLayer() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        PartDefinition head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("pipe", CubeListBuilder.create()
                        // Stem: from the mouth corner straight forward.
                        .texOffs(56, 16).addBox(1.0F, -2.0F, -7.0F, 1.0F, 1.0F, 3.0F)
                        // Bowl: hangs below the stem, open at the top where the ember glows.
                        .texOffs(56, 20).addBox(1.0F, -2.0F, -9.0F, 2.0F, 3.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}

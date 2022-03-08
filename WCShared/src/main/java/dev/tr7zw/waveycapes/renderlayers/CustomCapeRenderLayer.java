package dev.tr7zw.waveycapes.renderlayers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;

import dev.tr7zw.waveycapes.CapeHolder;
import dev.tr7zw.waveycapes.CapeMovement;
import dev.tr7zw.waveycapes.CapeRenderer;
import dev.tr7zw.waveycapes.CapeStyle;
import dev.tr7zw.waveycapes.VanillaCapeRenderer;
import dev.tr7zw.waveycapes.WaveyCapesBase;
import dev.tr7zw.waveycapes.WindMode;
import dev.tr7zw.waveycapes.sim.StickSimulation;
import dev.tr7zw.waveycapes.sim.StickSimulation.Point;
import dev.tr7zw.waveycapes.sim.StickSimulation.Stick;
import dev.tr7zw.waveycapes.support.ModSupport;
import dev.tr7zw.waveycapes.support.SupportManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CustomCapeRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    
    private static int partCount;
    private ModelPart[] customCape = new ModelPart[partCount];
    
    public CustomCapeRenderLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderLayerParent) {
        super(renderLayerParent);
        partCount = WaveyCapesBase.config.capeParts;
        buildMesh();
    }
    
    private void buildMesh() {
        customCape = new ModelPart[partCount];
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        for (int i = 0; i < partCount; i++)
            partDefinition.addOrReplaceChild("customCape_" + i,
                    CubeListBuilder.create().texOffs(0, (int) (i * (16f / partCount)))
                            .addBox(-5.0F, i * (16f / partCount), -1.0F, 10.0F, (16f / partCount), 1.0F, CubeDeformation.NONE, 1.0F, 0.5F),
                    PartPose.offset(0.0F, 0.0F, 0.0F));  
        ModelPart modelPart = partDefinition.bake(64,64);
        for (int i = 0; i < partCount; i++) {
            this.customCape[i] = modelPart.getChild("customCape_" + i);
        }
    }

    public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, AbstractClientPlayer abstractClientPlayer, float f, float g, float delta, float j, float k, float l) {
        CapeRenderer renderer = getCapeRenderer(abstractClientPlayer, multiBufferSource);
        if(renderer == null) return;
        ItemStack itemStack = abstractClientPlayer.getItemBySlot(EquipmentSlot.CHEST);
        if (itemStack.is(Items.ELYTRA))
            return;
        // smooth doesn't need more than 16
        if(WaveyCapesBase.config.capeStyle == CapeStyle.SMOOTH) {
            if(partCount != 16) {
                partCount = 16;
                buildMesh();
            }
        } else if(partCount != WaveyCapesBase.config.capeParts) {
            partCount = WaveyCapesBase.config.capeParts;
            buildMesh();
        }

        if (WaveyCapesBase.config.capeStyle == CapeStyle.SMOOTH) {
            renderSmoothCape(poseStack, abstractClientPlayer, delta);
        } else if (WaveyCapesBase.config.capeStyle == CapeStyle.BLOCKY) {
            ModelPart[] parts = customCape;
            for (int part = 0; part < partCount; part++) {
                ModelPart model = parts[part];
                modifyPoseStack(poseStack, abstractClientPlayer, delta, part);
                renderer.render(abstractClientPlayer, part, model, poseStack, multiBufferSource, i, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }

    private void renderSmoothCape(PoseStack poseStack, AbstractClientPlayer abstractClientPlayer, float delta) {
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, abstractClientPlayer.getCloakTextureLocation());
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        Matrix4f oldPositionMatrix = null;
        for (int part = 0; part < partCount; part++) {
            modifyPoseStack(poseStack, abstractClientPlayer, delta, part);

            if (oldPositionMatrix == null) {
                oldPositionMatrix = poseStack.last().pose();
            }

            if (part == 0) {
                addTopVertex(bufferBuilder, poseStack.last().pose(), oldPositionMatrix,
                        0.3F,
                        0,
                        0F,
                        -0.3F,
                        0,
                        -0.06F, part);
            }

            if (part == partCount - 1) {
                addBottomVertex(bufferBuilder, poseStack.last().pose(), poseStack.last().pose(),
                        0.3F,
                        (part + 1) * (0.96F / partCount),
                        0F,
                        -0.3F,
                        (part + 1) * (0.96F / partCount),
                        -0.06F, part);
            }

            addLeftVertex(bufferBuilder, poseStack.last().pose(), oldPositionMatrix,
                    -0.3F,
                    (part + 1) * (0.96F / partCount),
                    0F,
                    -0.3F,
                    part * (0.96F / partCount),
                    -0.06F, part);

            addRightVertex(bufferBuilder, poseStack.last().pose(), oldPositionMatrix,
                    0.3F,
                    (part + 1) * (0.96F / partCount),
                    0F,
                    0.3F,
                    part * (0.96F / partCount),
                    -0.06F, part);

            addBackVertex(bufferBuilder, poseStack.last().pose(), oldPositionMatrix,
                    0.3F,
                    (part + 1) * (0.96F / partCount),
                    -0.06F,
                    -0.3F,
                    part * (0.96F / partCount),
                    -0.06F, part);

            addFrontVertex(bufferBuilder, oldPositionMatrix, poseStack.last().pose(),
                    0.3F,
                    (part + 1) * (0.96F / partCount),
                    0F,
                    -0.3F,
                    part * (0.96F / partCount),
                    0F, part);

            oldPositionMatrix = poseStack.last().pose();
            poseStack.popPose();
        }

        bufferBuilder.end();
        BufferUploader.end(bufferBuilder);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
    }

    private void modifyPoseStack(PoseStack poseStack, AbstractClientPlayer abstractClientPlayer, float h, int part) {
        if(WaveyCapesBase.config.capeMovement == CapeMovement.BASIC_SIMULATION) {
            modifyPoseStackSimulation(poseStack, abstractClientPlayer, h, part);
            return;
        }
        modifyPoseStackVanilla(poseStack, abstractClientPlayer, h, part);
    }
    
    private void modifyPoseStackSimulation(PoseStack poseStack, AbstractClientPlayer abstractClientPlayer, float delta, int part) {
        StickSimulation simulation = ((CapeHolder)abstractClientPlayer).getSimulation();
        if(simulation.points.size() != partCount) {
            simulation.points.clear();
            simulation.sticks.clear();
            for (int i = 0; i < partCount; i++) {
                Point point = new Point();
                point.position.y = -i;
                point.locked = i == 0;
                simulation.points.add(point);
                if(i > 0) {
                    simulation.sticks.add(new Stick(simulation.points.get(i-1), point, 1f));
                }
            }
        }
        simulation.simulate();
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 0.125D);
        double d = Mth.lerp(delta, abstractClientPlayer.xCloakO, abstractClientPlayer.xCloak)
                - Mth.lerp(delta, abstractClientPlayer.xo, abstractClientPlayer.getX());
        double e = Mth.lerp(delta, abstractClientPlayer.yCloakO, abstractClientPlayer.yCloak)
                - Mth.lerp(delta, abstractClientPlayer.yo, abstractClientPlayer.getY());
        double m = Mth.lerp(delta, abstractClientPlayer.zCloakO, abstractClientPlayer.zCloak)
                - Mth.lerp(delta, abstractClientPlayer.zo, abstractClientPlayer.getZ());
        float n = abstractClientPlayer.yBodyRotO + abstractClientPlayer.yBodyRot - abstractClientPlayer.yBodyRotO;
        double o = Mth.sin(n * 0.017453292F);
        double p = -Mth.cos(n * 0.017453292F);
        float height = (float) e * 10.0F;
        height = Mth.clamp(height, -6.0F, 32.0F);
        if(part == 0) {
            simulation.points.get(0).position.x += (d * o + m * p);
            simulation.points.get(0).position.y = (float) (Mth.lerp(delta, abstractClientPlayer.yo, abstractClientPlayer.getY())*-16 + (abstractClientPlayer.isCrouching() ? 0 : -4));
        }
        
        float z = simulation.points.get(part).position.x - simulation.points.get(0).position.x;
        if(z > 0) {
            z = 0;
        }
        float y = simulation.points.get(part).position.y - simulation.points.get(0).position.y - part;
        
//        float sidewaysRotationOffset = (float) (d * p - m * o) * 100.0F;
//        sidewaysRotationOffset = Mth.clamp(sidewaysRotationOffset, -20.0F, 20.0F);
        float sidewaysRotationOffset = 0;
        float swing = (float) -Mth.atan2(y, z);
        swing = Math.max(swing, 0);
        if(swing != 0)
            swing = Mth.PI-swing;
        swing *= 57.2958;
        swing *= 2;
        
        float t = Mth.lerp(delta, abstractClientPlayer.oBob, abstractClientPlayer.bob);
        height += Mth.sin(Mth.lerp(delta, abstractClientPlayer.walkDistO, abstractClientPlayer.walkDist) * 6.0F) * 32.0F * t;
        if (abstractClientPlayer.isCrouching()) {
            height += 25.0F;
            poseStack.translate(0, 0.15F, 0);
        }

        float naturalWindSwing = getNatrualWindSwing(part);

        
        // vanilla rotating and wind
        poseStack.mulPose(Vector3f.XP.rotationDegrees(6.0F + height + naturalWindSwing));
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(sidewaysRotationOffset / 2.0F));
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - sidewaysRotationOffset / 2.0F));
        poseStack.translate(0, y/partCount, z/partCount); // movement from the simulation
        //offsetting so the rotation is on the cape part
        //float offset = (float) (part * (16 / partCount))/16; // to fold the entire cape into one position for debugging
        poseStack.translate(0, /*-offset*/ + (0.48/16) , - (0.48/16)); // (0.48/16)
        poseStack.translate(0, part * 1f/partCount, part * (0)/partCount);
        poseStack.mulPose(Vector3f.XP.rotationDegrees(-swing)); // apply actual rotation
        // undoing the rotation
        poseStack.translate(0, -part * 1f/partCount, -part * (0)/partCount);
        poseStack.translate(0, -(0.48/16), (0.48/16));
        
    }
    
    private void modifyPoseStackVanilla(PoseStack poseStack, AbstractClientPlayer abstractClientPlayer, float h, int part) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 0.125D);
        double d = Mth.lerp(h, abstractClientPlayer.xCloakO, abstractClientPlayer.xCloak)
                - Mth.lerp(h, abstractClientPlayer.xo, abstractClientPlayer.getX());
        double e = Mth.lerp(h, abstractClientPlayer.yCloakO, abstractClientPlayer.yCloak)
                - Mth.lerp(h, abstractClientPlayer.yo, abstractClientPlayer.getY());
        double m = Mth.lerp(h, abstractClientPlayer.zCloakO, abstractClientPlayer.zCloak)
                - Mth.lerp(h, abstractClientPlayer.zo, abstractClientPlayer.getZ());
        float n = abstractClientPlayer.yBodyRotO + abstractClientPlayer.yBodyRot - abstractClientPlayer.yBodyRotO;
        double o = Mth.sin(n * 0.017453292F);
        double p = -Mth.cos(n * 0.017453292F);
        float height = (float) e * 10.0F;
        height = Mth.clamp(height, -6.0F, 32.0F);
        float swing = (float) (d * o + m * p) * easeOutSine(1.0F/partCount*part)*100;
        swing = Mth.clamp(swing, 0.0F, 150.0F * easeOutSine(1F/partCount*part));
        float sidewaysRotationOffset = (float) (d * p - m * o) * 100.0F;
        sidewaysRotationOffset = Mth.clamp(sidewaysRotationOffset, -20.0F, 20.0F);
        float t = Mth.lerp(h, abstractClientPlayer.oBob, abstractClientPlayer.bob);
        height += Mth.sin(Mth.lerp(h, abstractClientPlayer.walkDistO, abstractClientPlayer.walkDist) * 6.0F) * 32.0F * t;
        if (abstractClientPlayer.isCrouching()) {
            height += 25.0F;
            poseStack.translate(0, 0.15F, 0);
        }

        float naturalWindSwing = getNatrualWindSwing(part);
        
        poseStack.mulPose(Vector3f.XP.rotationDegrees(6.0F + swing / 2.0F + height + naturalWindSwing));
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(sidewaysRotationOffset / 2.0F));
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - sidewaysRotationOffset / 2.0F));
    }
    
    private float getNatrualWindSwing(int part) {
        long highlightedPart = (System.currentTimeMillis() / 3) % 360;
        float relativePart = (float) (part + 1) / partCount;
        if (WaveyCapesBase.config.windMode == WindMode.WAVES) {
            return (float) (Math.sin(Math.toRadians((relativePart) * 360 - (highlightedPart))) * 3);
        }
//        if (WaveyCapesBase.config.windMode == WindMode.SLIGHT) {
//            return getWind(60);
//        }
        return 0;
    }

    private static void addBackVertex(BufferBuilder bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part) {
        float i;
        Matrix4f k;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;

            k = matrix;
            matrix = oldMatrix;
            oldMatrix = k;
        }

        float minU = .015625F;
        float maxU = .171875F;

        float minV = .03125F;
        float maxV = .53125F;

        float deltaV = maxV - minV;
        float vPerPart = deltaV / partCount;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        bufferBuilder.vertex(oldMatrix, x1, y2, z1).uv(maxU, minV).endVertex();
        bufferBuilder.vertex(oldMatrix, x2, y2, z1).uv(minU, minV).endVertex();
        bufferBuilder.vertex(matrix, x2, y1, z2).uv(minU, maxV).endVertex();
        bufferBuilder.vertex(matrix, x1, y1, z2).uv(maxU, maxV).endVertex();
    }

    private static void addFrontVertex(BufferBuilder bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part) {
        float i;
        Matrix4f k;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;

            k = matrix;
            matrix = oldMatrix;
            oldMatrix = k;
        }

        float minU = .1875F;
        float maxU = .34375F;

        float minV = .03125F;
        float maxV = .53125F;

        float deltaV = maxV - minV;
        float vPerPart = deltaV / partCount;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        bufferBuilder.vertex(oldMatrix, x1, y1, z1).uv(maxU, maxV).endVertex();
        bufferBuilder.vertex(oldMatrix, x2, y1, z1).uv(minU, maxV).endVertex();
        bufferBuilder.vertex(matrix, x2, y2, z2).uv(minU, minV).endVertex();
        bufferBuilder.vertex(matrix, x1, y2, z2).uv(maxU, minV).endVertex();
    }

    private static void addLeftVertex(BufferBuilder bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float minU = 0;
        float maxU = .015625F;

        float minV = .03125F;
        float maxV = .53125F;

        float deltaV = maxV - minV;
        float vPerPart = deltaV / partCount;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        bufferBuilder.vertex(matrix, x2, y1, z1).uv(maxU, maxV).endVertex();
        bufferBuilder.vertex(matrix, x2, y1, z2).uv(minU, maxV).endVertex();
        bufferBuilder.vertex(oldMatrix, x2, y2, z2).uv(minU, minV).endVertex();
        bufferBuilder.vertex(oldMatrix, x2, y2, z1).uv(maxU, minV).endVertex();
    }

    private static void addRightVertex(BufferBuilder bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float minU = .171875F;
        float maxU = .1875F;

        float minV = .03125F;
        float maxV = .53125F;

        float deltaV = maxV - minV;
        float vPerPart = deltaV / partCount;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        bufferBuilder.vertex(matrix, x2, y1, z2).uv(minU, maxV).endVertex();
        bufferBuilder.vertex(matrix, x2, y1, z1).uv(maxU, maxV).endVertex();
        bufferBuilder.vertex(oldMatrix, x2, y2, z1).uv(maxU, minV).endVertex();
        bufferBuilder.vertex(oldMatrix, x2, y2, z2).uv(minU, minV).endVertex();
    }

    private static void addBottomVertex(BufferBuilder bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float minU = .171875F;
        float maxU = .328125F;

        float minV = 0;
        float maxV = .03125F;

        float deltaV = maxV - minV;
        float vPerPart = deltaV / partCount;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        bufferBuilder.vertex(oldMatrix, x1, y2, z2).uv(maxU, minV).endVertex();
        bufferBuilder.vertex(oldMatrix, x2, y2, z2).uv(minU, minV).endVertex();
        bufferBuilder.vertex(matrix, x2, y1, z1).uv(minU, maxV).endVertex();
        bufferBuilder.vertex(matrix, x1, y1, z1).uv(maxU, maxV).endVertex();
    }

    private static void addTopVertex(BufferBuilder bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float minU = .015625F;
        float maxU = .171875F;

        float minV = 0;
        float maxV = .03125F;

        float deltaV = maxV - minV;
        float vPerPart = deltaV / partCount;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        bufferBuilder.vertex(oldMatrix, x1, y2, z1).uv(maxU, maxV).endVertex();
        bufferBuilder.vertex(oldMatrix, x2, y2, z1).uv(minU, maxV).endVertex();
        bufferBuilder.vertex(matrix, x2, y1, z2).uv(minU, minV).endVertex();
        bufferBuilder.vertex(matrix, x1, y1, z2).uv(maxU, minV).endVertex();
    }

    private static VanillaCapeRenderer vanillaCape = new VanillaCapeRenderer();
    
    private CapeRenderer getCapeRenderer(AbstractClientPlayer abstractClientPlayer, MultiBufferSource multiBufferSource) {
        for(ModSupport support : SupportManager.getSupportedMods()) {
            if(support.shouldBeUsed(abstractClientPlayer)) {
                return support.getRenderer();
            }
        }
        if (!abstractClientPlayer.isCapeLoaded() || abstractClientPlayer.isInvisible()
                || !abstractClientPlayer.isModelPartShown(PlayerModelPart.CAPE)
                || abstractClientPlayer.getCloakTextureLocation() == null) {
            return null;
        } else {
            vanillaCape.vertexConsumer = multiBufferSource
            .getBuffer(RenderType.entityCutout(abstractClientPlayer.getCloakTextureLocation()));
            return vanillaCape;
        }
    }
    
    private static int scale = 1000*60*60;
    
    /**
     * Returns between 0 and 2
     * 
     * @param posY
     * @return
     */
    private static float getWind(double posY) {
        float x = (System.currentTimeMillis()%scale)/10000f;
        float mod = Mth.clamp(1f/200f*(float)posY, 0f, 1f);
        return Mth.clamp((float) (Math.sin(2 * x) + Math.sin(Math.PI * x)) * mod, 0, 2);
    }
    
    
    /**
     * https://easings.net/#easeOutSine
     * 
     * @param x
     * @return
     */
    private static float easeOutSine(float x) {
        return Mth.sin((x * Mth.PI) / 2f);

      }
    
}

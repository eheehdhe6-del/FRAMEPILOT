package com.example.engine

import com.example.domain.model.AnimationFrame
import com.example.domain.model.DrawingStroke
import com.example.domain.model.JointPoint
import com.example.domain.model.Layer
import com.example.domain.model.RigAttachment
import com.example.domain.model.Scene
import com.example.domain.model.SceneShape
import com.example.domain.model.SceneText
import com.example.domain.model.ShapeType
import com.example.domain.model.SkeletonPose
import com.example.domain.model.StrokePoint
import com.example.domain.model.defaultStickmanJoints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class RigBonePreset(
    val id: String,
    val label: String,
    val shortLabel: String,
    val primaryJoint: String,
    val secondaryJoint: String?,
    val isRigidProp: Boolean
) {
    HEAD("head", "Head", "Head", "head", "neck", isRigidProp = true),
    TORSO("torso", "Torso / Jacket", "Torso", "neck", "hip", isRigidProp = false),
    LEFT_UPPER_ARM("leftUpperArm", "Left Upper Arm", "L Up Arm", "leftShoulder", "leftElbow", isRigidProp = false),
    LEFT_FOREARM("leftForearm", "Left Forearm", "L Forearm", "leftElbow", "leftHand", isRigidProp = false),
    LEFT_HAND("leftHand", "Left Hand", "L Hand", "leftHand", "leftElbow", isRigidProp = true),
    RIGHT_UPPER_ARM("rightUpperArm", "Right Upper Arm", "R Up Arm", "rightShoulder", "rightElbow", isRigidProp = false),
    RIGHT_FOREARM("rightForearm", "Right Forearm", "R Forearm", "rightElbow", "rightHand", isRigidProp = false),
    RIGHT_HAND("rightHand", "Right Hand", "R Hand", "rightHand", "rightElbow", isRigidProp = true),
    LEFT_THIGH("leftThigh", "Left Thigh", "L Thigh", "hip", "leftKnee", isRigidProp = false),
    LEFT_SHIN("leftShin", "Left Shin / Leg", "L Shin", "leftKnee", "leftFoot", isRigidProp = false),
    LEFT_FOOT("leftFoot", "Left Shoe / Foot", "L Foot", "leftFoot", "leftKnee", isRigidProp = true),
    RIGHT_THIGH("rightThigh", "Right Thigh", "R Thigh", "hip", "rightKnee", isRigidProp = false),
    RIGHT_SHIN("rightShin", "Right Shin / Leg", "R Shin", "rightKnee", "rightFoot", isRigidProp = false),
    RIGHT_FOOT("rightFoot", "Right Shoe / Foot", "R Foot", "rightFoot", "rightKnee", isRigidProp = true),
    WHOLE_RIG("wholeRig", "Whole Skeleton", "Whole", "hip", "neck", isRigidProp = true)
}

object RigEngine {

    private val defaultJoints by lazy { defaultStickmanJoints(0f, 0f) }

    private fun jointDist(joints: Map<String, JointPoint>, a: String, b: String): Float? {
        val ja = joints[a] ?: return null
        val jb = joints[b] ?: return null
        val d = hypot(jb.x - ja.x, jb.y - ja.y)
        return d.takeIf { it > 1e-2f }
    }

    /**
     * Computes rig scale from torso core joints (shoulders, neck-chest, chest-hip) so bending
     * arms/legs does not alter head/hand/stroke thickness scale, while whole-skeleton scaling updates cleanly.
     */
    fun computeCoreRigScale(joints: Map<String, JointPoint>): Float {
        if (joints.isEmpty()) return 1f
        val ratios = mutableListOf<Float>()
        listOf(
            "leftShoulder" to "rightShoulder",
            "neck" to "chest",
            "chest" to "hip",
            "head" to "neck"
        ).forEach { (a, b) ->
            val cur = jointDist(joints, a, b)
            val def = jointDist(defaultJoints, a, b)
            if (cur != null && def != null && def > 1e-2f) {
                ratios += cur / def
            }
        }
        return if (ratios.isNotEmpty()) {
            ratios.average().toFloat().coerceIn(0.2f, 4.0f)
        } else {
            1f
        }
    }

    fun createAttachment(
        preset: RigBonePreset,
        rigPose: SkeletonPose,
        rigLayerId: String? = null
    ): RigAttachment {
        val pA = rigPose.joints[preset.primaryJoint]
            ?: rigPose.joints["hip"]
            ?: rigPose.joints.values.firstOrNull()
            ?: JointPoint(540f, 960f)
        val pB = preset.secondaryJoint?.let { rigPose.joints[it] }
        return RigAttachment(
            rigLayerId = rigLayerId,
            primaryJoint = preset.primaryJoint,
            secondaryJoint = preset.secondaryJoint,
            refPrimaryX = pA.x,
            refPrimaryY = pA.y,
            refSecondaryX = pB?.x ?: pA.x,
            refSecondaryY = pB?.y ?: (pA.y - 40f),
            refRigScale = computeCoreRigScale(rigPose.joints),
            isRigidProp = preset.isRigidProp
        )
    }

    fun presetForAttachment(attachment: RigAttachment?): RigBonePreset? {
        attachment ?: return null
        return RigBonePreset.entries.firstOrNull {
            it.primaryJoint == attachment.primaryJoint &&
                it.secondaryJoint == attachment.secondaryJoint &&
                it.isRigidProp == attachment.isRigidProp
        } ?: RigBonePreset.entries.firstOrNull {
            it.primaryJoint == attachment.primaryJoint && it.secondaryJoint == attachment.secondaryJoint
        }
    }

    fun displayBoneLabel(attachment: RigAttachment?): String {
        attachment ?: return "Unattached"
        return presetForAttachment(attachment)?.shortLabel ?: attachment.primaryJoint
    }

    fun buildActivePosesByLayer(
        scene: Scene,
        activeFrame: AnimationFrame?
    ): Map<String, SkeletonPose> {
        val map = linkedMapOf<String, SkeletonPose>()
        scene.layers.forEach { layer ->
            val pose = activeFrame?.layerPoses?.get(layer.id) ?: layer.skeletonPose
            if (pose != null) {
                map[layer.id] = pose
            }
        }
        return map
    }

    fun resolveTargetPose(
        attachment: RigAttachment?,
        defaultLayerId: String,
        activePosesByLayer: Map<String, SkeletonPose>
    ): SkeletonPose? {
        attachment ?: return null
        val targetId = attachment.rigLayerId ?: defaultLayerId
        return activePosesByLayer[targetId]
    }

    fun computeCoreScaleRatio(attachment: RigAttachment, targetPose: SkeletonPose): Float {
        val curScale = computeCoreRigScale(targetPose.joints)
        val refScale = attachment.refRigScale.coerceAtLeast(0.05f)
        return (curScale / refScale).coerceIn(0.2f, 5.0f)
    }

    fun computeBoneAngleDeltaDeg(attachment: RigAttachment, targetPose: SkeletonPose): Float {
        val curA = targetPose.joints[attachment.primaryJoint] ?: return 0f
        val curB = attachment.secondaryJoint?.let { targetPose.joints[it] } ?: return 0f
        val refDx = attachment.refSecondaryX - attachment.refPrimaryX
        val refDy = attachment.refSecondaryY - attachment.refPrimaryY
        val curDx = curB.x - curA.x
        val curDy = curB.y - curA.y
        if (hypot(refDx, refDy) < 1e-3f || hypot(curDx, curDy) < 1e-3f) return 0f
        val refAngle = atan2(refDy.toDouble(), refDx.toDouble())
        val curAngle = atan2(curDy.toDouble(), curDx.toDouble())
        var deg = ((curAngle - refAngle) * 180.0 / PI).toFloat() % 360f
        if (deg > 180f) deg -= 360f
        if (deg <= -180f) deg += 360f
        return deg
    }

    fun transformPoint(
        x: Float,
        y: Float,
        attachment: RigAttachment,
        targetPose: SkeletonPose
    ): Pair<Float, Float> {
        val curA = targetPose.joints[attachment.primaryJoint] ?: return x to y
        val coreRatio = computeCoreScaleRatio(attachment, targetPose)
        val curB = attachment.secondaryJoint?.let { targetPose.joints[it] }
        if (curB == null) {
            return (curA.x + (x - attachment.refPrimaryX) * coreRatio) to
                (curA.y + (y - attachment.refPrimaryY) * coreRatio)
        }

        val refDx = attachment.refSecondaryX - attachment.refPrimaryX
        val refDy = attachment.refSecondaryY - attachment.refPrimaryY
        val refLen = hypot(refDx, refDy)
        val curDx = curB.x - curA.x
        val curDy = curB.y - curA.y
        val curLen = hypot(curDx, curDy)
        if (refLen < 1e-3f || curLen < 1e-3f) {
            return (curA.x + (x - attachment.refPrimaryX) * coreRatio) to
                (curA.y + (y - attachment.refPrimaryY) * coreRatio)
        }

        val ux = refDx / refLen
        val uy = refDy / refLen
        val nx = -uy
        val ny = ux

        val cux = curDx / curLen
        val cuy = curDy / curLen
        val cnx = -cuy
        val cny = cux

        val vx = x - attachment.refPrimaryX
        val vy = y - attachment.refPrimaryY
        val alongPx = vx * ux + vy * uy
        val perpPx = vx * nx + vy * ny

        return if (attachment.isRigidProp) {
            val sa = alongPx * coreRatio
            val sp = perpPx * coreRatio
            (curA.x + sa * cux + sp * cnx) to (curA.y + sa * cuy + sp * cny)
        } else {
            val alongFrac = alongPx / refLen
            val scaledAlong = alongFrac * curLen
            val scaledPerp = perpPx * coreRatio
            (curA.x + scaledAlong * cux + scaledPerp * cnx) to
                (curA.y + scaledAlong * cuy + scaledPerp * cny)
        }
    }

    /**
     * Converts a view/canvas drag delta (dx, dy) back into base stored coordinates for rigged items.
     */
    fun inverseDelta(
        dx: Float,
        dy: Float,
        attachment: RigAttachment,
        targetPose: SkeletonPose
    ): Pair<Float, Float> {
        val coreRatio = computeCoreScaleRatio(attachment, targetPose).coerceAtLeast(0.1f)
        val angleRad = -Math.toRadians(computeBoneAngleDeltaDeg(attachment, targetPose).toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        val rx = dx * cosA - dy * sinA
        val ry = dx * sinA + dy * cosA
        return (rx / coreRatio) to (ry / coreRatio)
    }

    fun deformStroke(
        stroke: DrawingStroke,
        targetPose: SkeletonPose?,
        canvasWidth: Int = 1080,
        canvasHeight: Int = 1920
    ): DrawingStroke {
        val att = stroke.rigAttachment ?: return stroke
        targetPose ?: return stroke
        val coreRatio = computeCoreScaleRatio(att, targetPose)
        val newPoints = stroke.points.map { pt ->
            val (nx, ny) = transformPoint(pt.x, pt.y, att, targetPose)
            StrokePoint(
                x = nx,
                y = ny,
                pressure = pt.pressure,
                normX = if (canvasWidth > 0) nx / canvasWidth.toFloat() else pt.normX,
                normY = if (canvasHeight > 0) ny / canvasHeight.toFloat() else pt.normY
            )
        }
        return stroke.copy(
            points = newPoints,
            strokeWidth = (stroke.strokeWidth * coreRatio).coerceAtLeast(1f)
        )
    }

    fun deformShape(
        shape: SceneShape,
        targetPose: SkeletonPose?
    ): SceneShape {
        val att = shape.rigAttachment ?: return shape
        targetPose ?: return shape
        val coreRatio = computeCoreScaleRatio(att, targetPose)

        if (shape.shapeType == ShapeType.LINE || shape.shapeType == ShapeType.ARROW) {
            val rawX0 = shape.x
            val rawY0 = shape.y
            val rawX1 = shape.x + shape.width
            val rawY1 = shape.y + shape.height
            val cx = (rawX0 + rawX1) / 2f
            val cy = (rawY0 + rawY1) / 2f
            val (rx0, ry0) = rotateAround(rawX0, rawY0, cx, cy, shape.rotationDeg)
            val (rx1, ry1) = rotateAround(rawX1, rawY1, cx, cy, shape.rotationDeg)
            val (nx0, ny0) = transformPoint(rx0, ry0, att, targetPose)
            val (nx1, ny1) = transformPoint(rx1, ry1, att, targetPose)
            return shape.copy(
                x = nx0,
                y = ny0,
                width = nx1 - nx0,
                height = ny1 - ny0,
                rotationDeg = 0f,
                strokeWidth = if (shape.strokeWidth > 0f) (shape.strokeWidth * coreRatio).coerceAtLeast(1f) else 0f
            )
        }

        val cx = shape.x + shape.width / 2f
        val cy = shape.y + shape.height / 2f
        val (newCx, newCy) = transformPoint(cx, cy, att, targetPose)
        val angleDelta = computeBoneAngleDeltaDeg(att, targetPose)

        var sx = coreRatio
        var sy = coreRatio
        if (!att.isRigidProp) {
            val curA = targetPose.joints[att.primaryJoint]
            val curB = att.secondaryJoint?.let { targetPose.joints[it] }
            val refDx = att.refSecondaryX - att.refPrimaryX
            val refDy = att.refSecondaryY - att.refPrimaryY
            val refLen = hypot(refDx, refDy)
            val curLen = if (curA != null && curB != null) hypot(curB.x - curA.x, curB.y - curA.y) else refLen
            if (refLen > 1e-2f && curLen > 1e-2f) {
                val lenRatio = (curLen / refLen).coerceIn(0.25f, 4.0f)
                val ux = refDx / refLen
                val uy = refDy / refLen
                val rotRad = Math.toRadians(shape.rotationDeg.toDouble())
                val lxX = cos(rotRad).toFloat()
                val lxY = sin(rotRad).toFloat()
                val lyX = -lxY
                val lyY = lxX
                val dotX = abs(lxX * ux + lxY * uy).coerceIn(0f, 1f)
                val dotY = abs(lyX * ux + lyY * uy).coerceIn(0f, 1f)
                sx = coreRatio + (lenRatio - coreRatio) * dotX
                sy = coreRatio + (lenRatio - coreRatio) * dotY
            }
        }

        val newW = shape.width * sx
        val newH = shape.height * sy
        return shape.copy(
            x = newCx - newW / 2f,
            y = newCy - newH / 2f,
            width = newW,
            height = newH,
            rotationDeg = (shape.rotationDeg + angleDelta) % 360f,
            strokeWidth = if (shape.strokeWidth > 0f) (shape.strokeWidth * coreRatio).coerceAtLeast(1f) else 0f
        )
    }

    fun deformText(
        text: SceneText,
        targetPose: SkeletonPose?
    ): SceneText {
        val att = text.rigAttachment ?: return text
        targetPose ?: return text
        val coreRatio = computeCoreScaleRatio(att, targetPose)
        val (nx, ny) = transformPoint(text.x, text.y, att, targetPose)
        val angleDelta = computeBoneAngleDeltaDeg(att, targetPose)
        return text.copy(
            x = nx,
            y = ny,
            fontSizeSp = (text.fontSizeSp * coreRatio).coerceIn(10f, 160f),
            rotationDeg = (text.rotationDeg + angleDelta) % 360f
        )
    }

    private fun rotateAround(x: Float, y: Float, cx: Float, cy: Float, deg: Float): Pair<Float, Float> {
        if (abs(deg) < 0.01f) return x to y
        val rad = Math.toRadians(deg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val dx = x - cx
        val dy = y - cy
        return (cx + dx * cosA - dy * sinA) to (cy + dx * sinA + dy * cosA)
    }

    /**
     * Auto-assigns the nearest logical skeleton bone/joint preset to unbound shapes and strokes.
     */
    fun findBestPresetForPoint(px: Float, py: Float, pose: SkeletonPose): RigBonePreset {
        val headPt = pose.joints["head"]
        if (headPt != null && hypot(px - headPt.x, py - headPt.y) <= 72f * computeCoreRigScale(pose.joints)) {
            return RigBonePreset.HEAD
        }
        val candidates = listOf(
            RigBonePreset.TORSO,
            RigBonePreset.LEFT_UPPER_ARM,
            RigBonePreset.LEFT_FOREARM,
            RigBonePreset.RIGHT_UPPER_ARM,
            RigBonePreset.RIGHT_FOREARM,
            RigBonePreset.LEFT_THIGH,
            RigBonePreset.LEFT_SHIN,
            RigBonePreset.RIGHT_THIGH,
            RigBonePreset.RIGHT_SHIN
        )
        var bestPreset = RigBonePreset.TORSO
        var bestDist = Float.MAX_VALUE
        candidates.forEach { preset ->
            val a = pose.joints[preset.primaryJoint] ?: return@forEach
            val b = preset.secondaryJoint?.let { pose.joints[it] } ?: return@forEach
            val d = CanvasTransformEngine.distancePointToSegment(px, py, a.x, a.y, b.x, b.y)
            if (d < bestDist) {
                bestDist = d
                bestPreset = preset
            }
        }
        // Check if closer to terminal feet/hands for crisp shoe/prop placement
        val footLeft = pose.joints["leftFoot"]
        val footRight = pose.joints["rightFoot"]
        val core = computeCoreRigScale(pose.joints)
        if (footLeft != null && hypot(px - footLeft.x, py - footLeft.y) <= 28f * core) {
            return RigBonePreset.LEFT_FOOT
        }
        if (footRight != null && hypot(px - footRight.x, py - footRight.y) <= 28f * core) {
            return RigBonePreset.RIGHT_FOOT
        }
        return bestPreset
    }

    fun autoBindLayerElements(
        layer: Layer,
        rigPose: SkeletonPose,
        rigLayerId: String = layer.id
    ): Pair<Layer, Int> {
        var attachedCount = 0
        val newShapes = layer.sceneShapes.map { sh ->
            if (sh.rigAttachment != null) sh
            else {
                val cx = sh.x + sh.width / 2f
                val cy = sh.y + sh.height / 2f
                val preset = findBestPresetForPoint(cx, cy, rigPose)
                attachedCount++
                sh.copy(rigAttachment = createAttachment(preset, rigPose, rigLayerId))
            }
        }
        val newStrokes = layer.sceneStrokes.map { st ->
            if (st.rigAttachment != null || st.points.isEmpty()) st
            else {
                val cx = st.points.map { it.x }.average().toFloat()
                val cy = st.points.map { it.y }.average().toFloat()
                val preset = findBestPresetForPoint(cx, cy, rigPose)
                attachedCount++
                st.copy(rigAttachment = createAttachment(preset, rigPose, rigLayerId))
            }
        }
        return layer.copy(sceneShapes = newShapes, sceneStrokes = newStrokes) to attachedCount
    }
}

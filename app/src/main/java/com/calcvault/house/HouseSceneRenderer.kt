package com.calcvault.house

import android.app.Activity
import android.graphics.Color
import android.util.Log
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * HouseSceneRenderer - Handles 3D rendering for the Dual Companion House
 * Improved to fix visibility, lighting, and grounding issues.
 */
class HouseSceneRenderer(
    private val activity: Activity,
    private val sceneView: SceneView,
    private val envWidth: Int,
    private val envHeight: Int
) {
    private var tomNode: ModelNode? = null
    private var angelaNode: ModelNode? = null
    private var groundNode: ModelNode? = null
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private val TAG = "HouseSceneRenderer"

    init {
        setupScene()
        loadModels()
    }

    private fun setupScene() {
        // Calibrate Camera for Talking Tom style framing
        sceneView.cameraNode.position = Position(x = 0.0f, y = 1.4f, z = 4.0f)
        sceneView.cameraNode.rotation = Rotation(x = -5f, y = 0f, z = 0f)
        
        // Add basic environment lighting
        sceneView.apply {
            mainLightNode?.let { light ->
                light.intensity = 180_000f
                light.position = Position(x = 2f, y = 8f, z = 4f)
                light.rotation = Rotation(x = -65f, y = 45f, z = 0f)
            }
            // Use a soft pleasant default background
            setBackgroundColor(Color.parseColor("#F3E8F6"))
        }
    }

    private fun loadModels() {
        scope.launch {
            try {
                // 1. Create a Ground Plane so characters don't float in void
                // Reusing tom.glb as a base if we don't have a plane.glb, 
                // but highly flattened and scaled.
                val groundInstance = sceneView.modelLoader.createModelInstance("models/tom.glb")
                if (groundInstance != null) {
                    groundNode = ModelNode(groundInstance).apply {
                        position = Position(x = 0.0f, y = -0.05f, z = 0.0f)
                        scale = Position(x = 12f, y = 0.01f, z = 12f)
                        // Make it look like a floor
                        isShadowReceiver = true
                    }
                    sceneView.addChildNode(groundNode!!)
                    Log.d(TAG, "Ground plane added")
                }

                // 2. Load Tom (Zain)
                val tomInstance = sceneView.modelLoader.createModelInstance("models/tom.glb")
                if (tomInstance != null) {
                    tomNode = ModelNode(tomInstance).apply {
                        position = Position(x = -0.8f, y = 0.0f, z = 0.0f)
                        scale = Position(x = 1.4f, y = 1.4f, z = 1.4f)
                        playAnimation(0, loop = true)
                    }
                    sceneView.addChildNode(tomNode!!)
                    Log.d(TAG, "Tom model loaded successfully")
                } else {
                    Log.e(TAG, "Failed to create Tom model instance")
                }

                // 3. Load Angela (Sanu)
                val angelaInstance = sceneView.modelLoader.createModelInstance("models/angela.glb")
                if (angelaInstance != null) {
                    angelaNode = ModelNode(angelaInstance).apply {
                        position = Position(x = 0.8f, y = 0.0f, z = 0.0f)
                        scale = Position(x = 1.3f, y = 1.3f, z = 1.3f)
                        playAnimation(0, loop = true)
                    }
                    sceneView.addChildNode(angelaNode!!)
                    Log.d(TAG, "Angela model loaded successfully")
                } else {
                    Log.e(TAG, "Failed to create Angela model instance")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading 3D models: ${e.message}")
            }
        }
    }

    /**
     * Update background when switching rooms
     */
    fun switchToRoom(room: HouseRoom, color: Int) {
        sceneView.setBackgroundColor(color)
        
        // Adjust ground color based on room type
        groundNode?.let { ground ->
             // Optional: change floor tint if API supports it
        }
        
        Log.d(TAG, "Switched room to ${room.name} with color: $color")
    }

    fun setCharacterState(state: DualCharacterState) {
        scope.launch {
            tomNode?.let { node ->
                // Map 2D environment coords to 3D Position
                // Center of 2D is center of 3D (0,0,0)
                val x = (state.zain.position.x / envWidth.toFloat() - 0.5f) * 5f
                val z = (state.zain.position.y / envHeight.toFloat() - 0.5f) * 5f
                node.position = Position(x = x, y = 0f, z = z)
                node.rotation = Rotation(x = 0f, y = state.zain.rotation, z = 0f)
            }

            angelaNode?.let { node ->
                val x = (state.sanu.position.x / envWidth.toFloat() - 0.5f) * 5f
                val z = (state.sanu.position.y / envHeight.toFloat() - 0.5f) * 5f
                node.position = Position(x = x, y = 0f, z = z)
                node.rotation = Rotation(x = 0f, y = state.sanu.rotation, z = 0f)
            }
        }
    }

    fun setDebugMode(enabled: Boolean) {}
    fun startAnimation() {}
    fun stopAnimation() {}

    fun destroy() {
        tomNode?.destroy()
        angelaNode?.destroy()
        groundNode?.destroy()
    }
}

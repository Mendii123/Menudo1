package com.example.armenu

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.armenu.ui.theme.ARMENUTheme
import com.example.armenu.ui.theme.Translucent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.core.HitResult
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import org.w3c.dom.Node
import kotlin.rem

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ARMENUTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Translucent
                ) {
                    Box(modifier = Modifier.fillMaxSize()){
                        val currentModel = remember {
                            mutableStateOf("burger")
                        }
                        ARScreen(currentModel.value)
                        Menu(modifier = Modifier.align(Alignment.BottomCenter)) {
                            currentModel.value = it
                        }

                    }
                }
            }
        }
    }
}

data class Food(var name: String, var imageId: Int)
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
@Composable
fun Menu(modifier: Modifier, onClick:(String)-> Unit) {
    var currentIndex by remember {
        mutableIntStateOf(0)
    }
    val itemsList = listOf(
        Food("ramen", R.drawable.ramen),
        Food("pizza", R.drawable.pizza),
        Food("burger", R.drawable.burger),
        Food("instant", R.drawable.instant),
        Food("momos", R.drawable.momos),
        Food("house", R.drawable.house )
    )
    fun updateIndex(offset: Int) {
        if (itemsList.isNotEmpty()) {
            currentIndex = (currentIndex + offset).let {
                (it % itemsList.size + itemsList.size) % itemsList.size
            }
        }
        onClick(itemsList[currentIndex].name)
    }

    Row(modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        IconButton(onClick = { updateIndex(-1) }) {
            Icon(painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24), contentDescription = "previous")
        }

        CircularImage(imageId = itemsList[currentIndex].imageId)

        IconButton(onClick = { updateIndex(1) }) {
            Icon(painter = painterResource(id = R.drawable.baseline_arrow_forward_ios_24), contentDescription = "next")
        }
    }
}


@Composable
fun CircularImage(
    modifier: Modifier = Modifier,
    imageId: Int
) {
    Box(modifier = modifier
        .size(140.dp)
        .clip(CircleShape)
        .border(width = 3.dp, Translucent)
    ) {
        Image(painter = painterResource(id = imageId), contentDescription = null, modifier = Modifier.size(140.dp),contentScale = ContentScale.FillBounds)
    }
}

@Composable
fun ARScreen(model: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Core Filament/SceneView objects
        val engine = rememberEngine()
        val modelLoader = rememberModelLoader(engine)
        val materialLoader = rememberMaterialLoader(engine)

        // Scene graph you’ll add nodes into
        val childNodes = rememberNodes()

        var placedAnchorNode by remember { mutableStateOf<AnchorNode?>(null) }
        var isPlaneTracked by remember { mutableStateOf(false) }

        // AR frame + current center-screen hit result
        var validHit by remember { mutableStateOf<HitResult?>(null) }

        // Screen center in PX for continuous hit test
        val density = LocalDensity.current
        val centerXpx = with(density) { (maxWidth / 2).toPx() }
        val centerYpx = with(density) { (maxHeight / 2).toPx() }

        // Preload the base Model once per 'model' parameter
        val baseModel = remember(model) {
            // loads "assets/models/<model>.glb"
            modelLoader.createModel("models/$model.glb")
        }

        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            // Camera background stream for AR
            cameraStream = rememberARCameraStream(materialLoader),
            // Don’t draw plane visuals (old code hid it later; here we just turn it off)
            planeRenderer = false,
            // Session configuration replaces old direct property setters
            sessionConfiguration = { session, config ->
                config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                config.depthMode = Config.DepthMode.AUTOMATIC
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
            },
            // Keep the latest frame and compute a center hit to enable the button
            onSessionUpdated = { _, updatedFrame ->
                val centerHit = updatedFrame
                    .hitTest(centerXpx, centerYpx)
                    .firstOrNull { hit ->
                        val plane = hit.trackable as? Plane ?: return@firstOrNull false
                        plane.trackingState == TrackingState.TRACKING && plane.isPoseInPolygon(hit.hitPose)
                    }
                validHit = centerHit
                isPlaneTracked = centerHit != null
            },
            // Hook in the scene graph
            childNodes = childNodes,
            onSessionFailed = { e -> Log.e("AR", "ARCore session error", e) }
        )

        // Same UX: show “Place It” when tracking a valid surface at screen center
        if (validHit != null && placedAnchorNode == null) {
            Button(
                onClick = {
                    val anchor = validHit?.createAnchorOrNull() ?: return@Button
                    val anchorNode = AnchorNode(engine = engine, anchor = anchor)

                    // Prevent multiple placements until the current one is removed
                    if (placedAnchorNode != null) return@Button

                    // Create a fresh instance from the preloaded model, scale like before
                    val instance = modelLoader.createInstance(baseModel) ?: return@Button
                    anchorNode.addChildNode(
                        ModelNode(modelInstance = instance, scaleToUnits = 1.8f).apply {
                            // optional: nudge forward if you prefer
                            position =
                                _root_ide_package_.io.github.sceneview.math.Position(z = 0.0f)
                        }
                    )
                    childNodes += anchorNode
                    placedAnchorNode = anchorNode
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            ) { Text("Place It") }
        }

        CenterReticle(
            isActive = isPlaneTracked,
            modifier = Modifier.align(Alignment.Center)
        )

        if (placedAnchorNode != null) {
            Button(
                onClick = {
                    placedAnchorNode?.let { anchorNode ->
                        childNodes -= anchorNode
                        anchorNode.anchor?.detach()
                        placedAnchorNode = null
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text("Back")
            }
        }
    }
}


@Composable
private fun CenterReticle(isActive: Boolean, modifier: Modifier = Modifier) {
    val color = if (isActive) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.75f)
    Canvas(modifier = modifier.size(64.dp)) {
        val strokeWidth = 2.dp.toPx()
        val radius = size.minDimension / 2f
        drawCircle(color = color, radius = radius, style = Stroke(width = strokeWidth))

        val halfWidth = size.width / 2f
        val halfHeight = size.height / 2f
        drawLine(color, Offset(halfWidth, 0f), Offset(halfWidth, size.height), strokeWidth)
        drawLine(color, Offset(0f, halfHeight), Offset(size.width, halfHeight), strokeWidth)
    }
}

package me.arianb.usb_hid_client.ui.standalone_screens

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.ui.theme.CornerLargeIncreased
import me.arianb.usb_hid_client.ui.theme.CornerMedium
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.PaddingSmall
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar
import me.arianb.usb_hid_client.ui.utils.getColorByTheme

class HelpScreen : Screen {
    @Composable
    override fun Content() {
        HelpPage()
    }
}

private data class FaqItem(
    @StringRes val titleResource: Int,
    @StringRes val textResource: Int,
    val icon: ImageVector = Icons.Outlined.HelpOutline,
    val hasHyperLink: Boolean = false,
)

@Composable
fun HelpPage() {
    val faqItems = remember {
        arrayOf(
            FaqItem(R.string.help_faq_q1, R.string.help_faq_a1, icon = Icons.Outlined.HelpOutline),
            FaqItem(R.string.help_faq_spen_title, R.string.help_faq_spen_content, icon = Icons.Outlined.Draw),
            FaqItem(R.string.help_faq_touchpad_title, R.string.help_faq_touchpad_content, icon = Icons.Outlined.Mouse),
            FaqItem(R.string.help_faq_q2, R.string.help_faq_a2, icon = Icons.Outlined.Keyboard),
            FaqItem(R.string.help_faq_setup_title, R.string.help_faq_setup_content, icon = Icons.Outlined.Build),
            FaqItem(R.string.help_faq_q5, R.string.help_faq_a5, icon = Icons.Outlined.Info, hasHyperLink = true),
        )
    }

    BasicPage(
        topBar = { HelpTopBar() },
        padding = PaddingValues(horizontal = PaddingNormal, vertical = PaddingSmall),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top),
        scrollable = true,
    ) {
        for (item in faqItems) {
            key(item) {
                ExpandableFaqCard(
                    titleResource = item.titleResource,
                    textResource = item.textResource,
                    icon = item.icon,
                    useLegacyTextViewForText = item.hasHyperLink
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.help),
        scrollBehavior = pinnedScrollBehavior()
    )
}

@Composable
fun ExpandableFaqCard(
    @StringRes titleResource: Int,
    @StringRes textResource: Int,
    icon: ImageVector = Icons.Outlined.HelpOutline,
    useLegacyTextViewForText: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val degrees by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "arrowDegrees"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(titleResource),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .rotate(degrees)
                        .padding(start = 8.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 44.dp)
                ) {
                    if (useLegacyTextViewForText) {
                        ComposeTextView(
                            textResource,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = stringResource(textResource),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper Composable for HTML/hyperlinks in text.
 */
@Composable
fun ComposeTextView(
    @StringRes id: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val text = LocalResources.current.getText(id)
    val textColor = getColorByTheme()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                textSize = style.fontSize.value
                if (style.lineHeight.isSp) {
                    TextViewCompat.setLineHeight(this, TypedValue.COMPLEX_UNIT_SP, style.lineHeight.value)
                }
            }
        },
        update = {
            it.text = text
            it.setTextColor(textColor)
        }
    )
}

@DarkLightModePreviews
@Composable
private fun HelpScreenPreview() {
    Navigator(HelpScreen())
}

package com.denguewatch.bd.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.denguewatch.bd.ui.theme.DwIcons

/**
 * A single surveillance figure, drawn for sharing rather than for reading in
 * the app.
 *
 * Fixed size and fixed colours on purpose. This is rasterised to a PNG that
 * will be seen on someone else's phone in a chat thread, so it must not inherit
 * the sender's dark mode, font-size setting or accent colour — three things
 * every other view in this app deliberately does inherit.
 *
 * Sizes are expressed in `dp` and the card is captured at a density of 1, so
 * one dp is one pixel and the output is exactly [WIDTH] × [HEIGHT].
 */
object ShareCard {
    /**
     * 4:5 is the tallest a feed will show without cropping, which is what
     * Facebook, Instagram and WhatsApp status all favour.
     */
    const val WIDTH = 1080
    const val HEIGHT = 1350
}

@Composable
fun ShareCardView(
    content: AreaShareCard.Content,
    riskTint: Color,
    appName: String,
    tagline: String,
    modifier: Modifier = Modifier,
) {
    val white = Color.White

    Box(
        modifier
            .width(ShareCard.WIDTH.dp)
            .height(ShareCard.HEIGHT.dp)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF121722), Color(0xFF1F2636)),
                ),
            ),
    ) {
        // A faint wash of the risk colour, so the card reads at a glance in a
        // feed even before the words are.
        Box(
            Modifier
                .fillMaxWidth()
                .height(ShareCard.HEIGHT.dp)
                .background(
                    Brush.radialGradient(
                        listOf(riskTint.copy(alpha = 0.22f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(
                            ShareCard.WIDTH.toFloat(),
                            0f,
                        ),
                        radius = 900f,
                    ),
                ),
        )

        Column(Modifier.padding(72.dp).height((ShareCard.HEIGHT).dp)) {
            Text(
                appName.uppercase(),
                style = TextStyle(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 4.sp,
                    color = white.copy(alpha = 0.75f),
                ),
            )

            Spacer(Modifier.height(60.dp).weight(1f, fill = true))

            // No line cap. The card has room for a name to wrap, and capping
            // it would cut "Dhaka (outside city)" with no mark on an image
            // that travels without the app to correct it.
            Text(
                content.areaName,
                style = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Bold, color = white),
            )

            Row(
                Modifier.padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(riskTint))
                Text(
                    content.riskLabel.uppercase(),
                    style = TextStyle(
                        fontSize = 60.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = riskTint,
                    ),
                )
            }

            content.weeklyChange?.let { change ->
                Row(
                    Modifier.padding(top = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (content.weeklyChangeIsUp) DwIcons.trendUp else DwIcons.trendDown,
                        contentDescription = null,
                        tint = white.copy(alpha = 0.9f),
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        change,
                        style = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = white.copy(alpha = 0.9f),
                        ),
                    )
                }
            }

            Spacer(Modifier.height(40.dp).weight(1f, fill = true))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Figure(content.incidence, content.incidenceUnit, Modifier.weight(1f))
                Box(
                    Modifier
                        .padding(horizontal = 44.dp)
                        .width(1.dp)
                        .height(108.dp)
                        .background(white.copy(alpha = 0.18f)),
                )
                Figure(content.seasonCases, content.seasonCasesLabel, Modifier.weight(1f))
            }

            Spacer(Modifier.height(50.dp).weight(1f, fill = true))

            // Attribution is not decoration: a number shared out of the app
            // without its source and date is a rumour.
            Text(
                content.sourceLine,
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = white.copy(alpha = 0.62f),
                ),
            )
            Text(
                tagline,
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(fontSize = 26.sp, color = white.copy(alpha = 0.42f)),
            )
        }
    }
}

@Composable
private fun Figure(value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            value,
            style = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.White),
        )
        Text(
            unit,
            style = TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f),
            ),
        )
    }
}

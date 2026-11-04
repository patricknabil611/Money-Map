package com.example.data.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

data class NetflixAvatar(
  val key: String,
  val title: String,
  val drawableRes: Int? = null,
  val vectorIcon: ImageVector? = null,
  val gradientColors: List<Color> = listOf(Color(0xFFE50914), Color(0xFF831010))
)

object AvatarRegistry {
  val ALL_AVATARS = listOf(
    NetflixAvatar(
      key = "patrick",
      title = "Patrick (Photo)",
      drawableRes = R.drawable.img_avatar_patrick,
      gradientColors = listOf(Color(0xFF2A2A38), Color(0xFF0F0F14))
    ),
    NetflixAvatar(
      key = "red_dragon",
      title = "Red Dragon",
      drawableRes = R.drawable.img_avatar_red_dragon,
      gradientColors = listOf(Color(0xFFE50914), Color(0xFF7F1D1D))
    ),
    NetflixAvatar(
      key = "classic_smile",
      title = "Classic Cool",
      drawableRes = R.drawable.img_avatar_classic_smile,
      gradientColors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
    ),
    NetflixAvatar(
      key = "cyber_wolf",
      title = "Cyber Wolf",
      drawableRes = R.drawable.img_avatar_cyber_wolf,
      gradientColors = listOf(Color(0xFFA855F7), Color(0xFF6B21A8))
    ),
    NetflixAvatar(
      key = "golden_phoenix",
      title = "Golden Phoenix",
      drawableRes = R.drawable.img_avatar_golden_phoenix,
      gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFB45309))
    ),
    NetflixAvatar(
      key = "neon_cat",
      title = "Neon Panther",
      vectorIcon = Icons.Default.Pets,
      gradientColors = listOf(Color(0xFFEC4899), Color(0xFF9D174D))
    ),
    NetflixAvatar(
      key = "cosmic_astronaut",
      title = "Cosmic Voyager",
      vectorIcon = Icons.Default.RocketLaunch,
      gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A))
    ),
    NetflixAvatar(
      key = "shadow_ninja",
      title = "Shadow Guardian",
      vectorIcon = Icons.Default.Security,
      gradientColors = listOf(Color(0xFF10B981), Color(0xFF065F46))
    ),
    NetflixAvatar(
      key = "gold_star",
      title = "Gold Prodigy",
      vectorIcon = Icons.Default.Star,
      gradientColors = listOf(Color(0xFFFBBF24), Color(0xFFD97706))
    )
  )

  fun getAvatar(key: String?): NetflixAvatar {
    return ALL_AVATARS.find { it.key == key } ?: ALL_AVATARS[0]
  }
}

@Composable
fun ProfileAvatarView(
  avatarKey: String?,
  modifier: Modifier = Modifier,
  shape: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
  val avatar = AvatarRegistry.getAvatar(avatarKey)
  Box(
    modifier = modifier
      .clip(shape)
      .background(Brush.linearGradient(avatar.gradientColors)),
    contentAlignment = Alignment.Center
  ) {
    if (avatar.drawableRes != null) {
      Image(
        painter = painterResource(id = avatar.drawableRes),
        contentDescription = avatar.title,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
      )
    } else if (avatar.vectorIcon != null) {
      Icon(
        imageVector = avatar.vectorIcon,
        contentDescription = avatar.title,
        tint = Color.White,
        modifier = Modifier.fillMaxSize(0.55f)
      )
    }
  }
}

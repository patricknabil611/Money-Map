package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.PhotoReceipt
import com.example.data.model.Sha2aItem
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import java.io.File

@Composable
fun Sha2aScreen(
  items: List<Sha2aItem>,
  photos: List<PhotoReceipt>,
  onAddItem: (String) -> Unit,
  onEditItem: (String) -> Unit,
  onOpenPhoto: (PhotoReceipt) -> Unit,
  onAddPhotos: (List<Uri>) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val context = LocalContext.current

  val inItems = remember(items) {
    items.filter { it.side == "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
  }
  val outItems = remember(items) {
    items.filter { it.side != "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
  }

  val totalIn = remember(inItems) { inItems.sumOf { it.amount } }
  val totalOut = remember(outItems) { outItems.sumOf { it.amount } }
  val diff = totalIn - totalOut

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      onAddPhotos(uris)
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp, vertical = 6.dp)
  ) {
    // Top 3 Stat Tiles
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Total in tile
        TileCard(
          title = "Total in",
          amount = totalIn,
          backgroundColor = Color(0xFF157552),
          modifier = Modifier.weight(1f)
        )
        // Total out tile
        TileCard(
          title = "Total out",
          amount = totalOut,
          backgroundColor = Color(0xFFB9372F),
          modifier = Modifier.weight(1f)
        )
        // Difference tile
        TileCard(
          title = "Difference",
          amount = diff,
          backgroundColor = Color(0xFF2A50CC),
          modifier = Modifier.weight(1f)
        )
      }
    }

    // In List Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = CardDefaults.outlinedCardBorder()
      ) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("In", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            OutlinedButton(
              onClick = { onAddItem("in") },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.testTag("add_sha2a_in_btn")
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.income)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add", color = colors.income, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
          }

          if (inItems.isEmpty()) {
            Text(
              "Nothing here yet.",
              color = colors.muted,
              fontSize = 14.sp,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
            )
          } else {
            inItems.forEachIndexed { i, itm ->
              if (i > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line))
              }
              Sha2aRowItem(
                index = i + 1,
                item = itm,
                isIncome = true,
                onClick = { onEditItem(itm.id) }
              )
            }
          }
        }
      }
    }

    // Out List Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = CardDefaults.outlinedCardBorder()
      ) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Out", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            OutlinedButton(
              onClick = { onAddItem("out") },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.testTag("add_sha2a_out_btn")
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.expense)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add", color = colors.expense, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
          }

          if (outItems.isEmpty()) {
            Text(
              "Nothing here yet.",
              color = colors.muted,
              fontSize = 14.sp,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
            )
          } else {
            outItems.forEachIndexed { i, itm ->
              if (i > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line))
              }
              Sha2aRowItem(
                index = i + 1,
                item = itm,
                isIncome = false,
                onClick = { onEditItem(itm.id) }
              )
            }
          }
        }
      }
    }

    // Andrew's Receipts Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 28.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = CardDefaults.outlinedCardBorder()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Andrew’s receipts", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            OutlinedButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.testTag("upload_receipts_btn")
            ) {
              Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.head)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Upload photos", color = colors.ink, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          if (photos.isEmpty()) {
            Text(
              "Upload photos of Andrew’s quotes and receipts to keep them with the numbers.",
              color = colors.muted,
              fontSize = 13.5.sp,
              modifier = Modifier.padding(vertical = 14.dp)
            )
          } else {
            // Photo Grid (3 columns)
            val chunked = remember(photos) { photos.chunked(3) }
            chunked.forEach { rowPhotos ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                rowPhotos.forEach { photo ->
                  val file = remember(photo.localFileName) {
                    File(context.filesDir, "receipts/${photo.localFileName}")
                  }
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .aspectRatio(0.75f)
                      .clip(RoundedCornerShape(10.dp))
                      .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                      .background(colors.surface2)
                      .clickable { onOpenPhoto(photo) }
                  ) {
                    AsyncImage(
                      model = file,
                      contentDescription = photo.caption,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()
                    )
                    if (photo.caption.isNotBlank()) {
                      Box(
                        modifier = Modifier
                          .fillMaxWidth()
                          .align(Alignment.BottomCenter)
                          .background(
                            Brush.verticalGradient(
                              colors = listOf(Color.Transparent, Color(0xAA000000))
                            )
                          )
                          .padding(horizontal = 6.dp, vertical = 4.dp)
                      ) {
                        Text(
                          text = photo.caption,
                          color = Color.White,
                          fontSize = 11.sp,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                    }
                  }
                }
                // Fill empty slots if last row has fewer than 3
                repeat(3 - rowPhotos.size) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun TileCard(
  title: String,
  amount: Double,
  backgroundColor: Color,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = backgroundColor)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        ArabicNumberHelper.fmt(amount),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun Sha2aRowItem(
  index: Int,
  item: Sha2aItem,
  isIncome: Boolean,
  onClick: () -> Unit
) {
  val colors = LocalMoneyMapColors.current

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "$index",
      color = colors.muted,
      fontSize = 13.sp,
      modifier = Modifier.width(26.dp)
    )

    Text(
      text = item.name,
      fontWeight = FontWeight.Medium,
      fontSize = 14.sp,
      color = colors.ink,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    Text(
      text = ArabicNumberHelper.fmt(item.amount),
      fontWeight = FontWeight.Bold,
      fontSize = 15.sp,
      color = if (isIncome) colors.income else colors.expense
    )
  }
}

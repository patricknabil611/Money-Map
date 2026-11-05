package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.AppIconButton
import com.example.ui.components.AppPrimaryButton
import com.example.ui.components.AppSecondaryButton
import com.example.ui.theme.AppShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PhotoReceipt
import com.example.data.model.ProjectEntity
import com.example.data.model.Sha2aItem
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper

@Composable
fun ProjectsScreen(
  projects: List<ProjectEntity>,
  allItems: List<Sha2aItem>,
  allPhotos: List<PhotoReceipt>,
  selectedProjectId: String?,
  onSelectProject: (String?) -> Unit,
  onAddProject: (String, String) -> Unit,
  onAddItem: (String) -> Unit,
  onEditItem: (String) -> Unit,
  onOpenPhoto: (PhotoReceipt) -> Unit,
  onAddPhotos: (List<Uri>) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  var showAddProjectDialog by remember { mutableStateOf(false) }

  // If inside a project, intercept back to return to projects list
  BackHandler(enabled = selectedProjectId != null) {
    onSelectProject(null)
  }

  if (selectedProjectId != null) {
    // Project Detail Page (Sha2a UI)
    val currentProject = projects.find { it.id == selectedProjectId }
      ?: ProjectEntity(id = selectedProjectId, name = "Sha2a")

    val projectItems = remember(allItems, selectedProjectId) {
      allItems.filter { it.projectId == selectedProjectId || (selectedProjectId == "sha2a" && it.projectId.isBlank()) }
    }
    val projectPhotos = remember(allPhotos, selectedProjectId) {
      allPhotos.filter { it.projectId == selectedProjectId || (selectedProjectId == "sha2a" && it.projectId.isBlank()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
      // Top Navigation Header with Back button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        AppIconButton(
          onClick = { onSelectProject(null) },
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back to projects",
          modifier = Modifier.testTag("back_to_projects_btn")
        )

        Spacer(modifier = Modifier.width(4.dp))

        Column {
          Text(
            text = currentProject.name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink
          )
          if (currentProject.description.isNotBlank()) {
            Text(
              text = currentProject.description,
              fontSize = 12.sp,
              color = colors.muted
            )
          }
        }
      }

      // Project Content (Sha2a UI)
      Sha2aScreen(
        items = projectItems,
        photos = projectPhotos,
        onAddItem = onAddItem,
        onEditItem = onEditItem,
        onOpenPhoto = onOpenPhoto,
        onAddPhotos = onAddPhotos
      )
    }
  } else {
    // Projects Overview List Screen
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Projects",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            modifier = Modifier.weight(1f)
          )

          AppPrimaryButton(
            onClick = { showAddProjectDialog = true },
            text = "Add project",
            icon = Icons.Default.Add,
            modifier = Modifier.testTag("add_project_btn")
          )
        }
      }

      // Display all projects
      items(projects, key = { it.id }) { project ->
        val projectItems = remember(allItems, project.id) {
          allItems.filter { it.projectId == project.id || (project.id == "sha2a" && it.projectId.isBlank()) }
        }
        val projectPhotos = remember(allPhotos, project.id) {
          allPhotos.filter { it.projectId == project.id || (project.id == "sha2a" && it.projectId.isBlank()) }
        }

        val totalIn = remember(projectItems) {
          projectItems.filter { it.side == "in" }.sumOf { it.amount }
        }
        val totalOut = remember(projectItems) {
          projectItems.filter { it.side != "in" }.sumOf { it.amount }
        }
        val diff = totalIn - totalOut

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onSelectProject(project.id) }
            .testTag("project_card_${project.id}"),
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface),
          border = CardDefaults.outlinedCardBorder()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            // Project Name & Badges
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surface2),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Folder, contentDescription = null, tint = colors.leftover, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = project.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                  )
                  if (project.description.isNotBlank()) {
                    Text(
                      text = project.description,
                      fontSize = 12.sp,
                      color = colors.muted
                    )
                  }
                }
              }

              Icon(Icons.Default.ChevronRight, contentDescription = "Open project", tint = colors.muted)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Total In, Total Out, Difference metrics under project name
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Total in
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(AppShapes.Small)
                  .background(colors.incomeSoft)
                  .padding(8.dp)
              ) {
                Column {
                  Text("Total in", color = colors.income, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                  Text(
                    text = ArabicNumberHelper.fmt(totalIn),
                    color = colors.income,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }

              // Total out
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(AppShapes.Small)
                  .background(colors.expenseSoft)
                  .padding(8.dp)
              ) {
                Column {
                  Text("Total out", color = colors.expense, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                  Text(
                    text = ArabicNumberHelper.fmt(totalOut),
                    color = colors.expense,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }

              // Difference
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(AppShapes.Small)
                  .background(colors.leftoverSoft)
                  .padding(8.dp)
              ) {
                Column {
                  Text("Difference", color = colors.leftover, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                  Text(
                    text = ArabicNumberHelper.fmt(diff),
                    color = if (diff < 0) colors.expense else colors.leftover,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "${projectItems.size} items • ${projectPhotos.size} receipts",
                color = colors.muted,
                fontSize = 12.sp
              )
              Text(
                text = "Tap to open →",
                color = colors.leftover,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
              )
            }
          }
        }
      }
    }
  }

  // Dialog: Add Project
  if (showAddProjectDialog) {
    var newProjName by remember { mutableStateOf("") }
    var newProjDesc by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showAddProjectDialog = false },
      shape = AppShapes.Dialog,
      title = { Text("New Project", fontWeight = FontWeight.Bold) },
      text = {
        Column {
          OutlinedTextField(
            value = newProjName,
            onValueChange = { newProjName = it; dialogError = null },
            label = { Text("Project Name") },
            placeholder = { Text("e.g. Sahel Villa, Office, Wedding") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = newProjDesc,
            onValueChange = { newProjDesc = it },
            label = { Text("Description (Optional)") },
            placeholder = { Text("Renovation, furniture, etc.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          dialogError?.let { err ->
            Text(err, color = colors.expense, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
          }
        }
      },
      confirmButton = {
        AppPrimaryButton(
          onClick = {
            if (newProjName.isBlank()) {
              dialogError = "Enter a project name."
              return@AppPrimaryButton
            }
            onAddProject(newProjName.trim(), newProjDesc.trim())
            showAddProjectDialog = false
          },
          text = "Create"
        )
      },
      dismissButton = {
        AppSecondaryButton(
          onClick = { showAddProjectDialog = false },
          text = "Cancel"
        )
      }
    )
  }
}

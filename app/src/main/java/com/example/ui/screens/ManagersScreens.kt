package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CustomerEntity
import com.example.data.ProductEntity
import com.example.data.UserSession
import com.example.ui.components.ProvideSafeTextToolbar
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.RedDue
import com.example.ui.theme.TealAccent
import java.util.Locale

@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    isAdmin: Boolean,
    onSaveCustomer: (name: String, details: String) -> Unit,
    onDeleteCustomer: (Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (isAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Customer Manager", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Customer Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("new_customer_name")
                        )
                        OutlinedTextField(
                            value = details,
                            onValueChange = { details = it },
                            label = { Text("Address / Mobile") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("new_customer_details")
                        )
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onSaveCustomer(name, details)
                                    name = ""
                                    details = ""
                                } else {
                                    onSaveCustomer("", details)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_customer_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(" Save Customer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (customers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("No customers saved yet", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(customers, key = { it.id }) { cust ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cust.name, style = MaterialTheme.typography.titleMedium)
                            if (cust.details.isNotBlank()) {
                                Text(cust.details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (isAdmin) {
                            IconButton(onClick = { onDeleteCustomer(cust.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete customer", tint = RedDue)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun ProductsScreen(
    products: List<ProductEntity>,
    user: UserSession,
    onAddProduct: (name: String, unit: String, rateText: String) -> Unit,
    onUpdateProduct: (id: Long, name: String, unit: String, rateText: String) -> Unit,
    onDeleteProduct: (Long) -> Unit
) {
    val canManageProducts = !user.isStaff && user.isAdmin
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Nos") }
    var rate by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (canManageProducts) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Product Manager (Admin)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Add new CCTV products or tap Edit on any item below to update its name, unit, or price.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Product Name (e.g. 4 Channel DVR)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("new_product_name")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                label = { Text("Unit") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("new_product_unit")
                            )
                            OutlinedTextField(
                                value = rate,
                                onValueChange = { rate = it },
                                label = { Text("Default Rate (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1.2f).testTag("new_product_rate")
                            )
                        }
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onAddProduct(name, unit, rate)
                                    name = ""
                                    rate = ""
                                } else {
                                    onAddProduct("", unit, rate)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_product_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(" Add Product", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Product Catalog (Read-Only)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Staff users cannot add, edit, or delete products.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (products.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("No products found", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(products, key = { it.id }) { prod ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Unit: ${prod.unit} · Rate: ₹${String.format(Locale.US, "%.2f", prod.defaultRate)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canManageProducts) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        editingProduct = prod
                                    },
                                    modifier = Modifier.testTag("edit_product_${prod.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit product", tint = TealAccent)
                                }
                                IconButton(
                                    onClick = { onDeleteProduct(prod.id) },
                                    modifier = Modifier.testTag("delete_product_${prod.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete product", tint = RedDue)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    val productToEdit = editingProduct
    if (canManageProducts && productToEdit != null) {
        EditProductDialog(
            product = productToEdit,
            onDismiss = { editingProduct = null },
            onSave = { updatedName, updatedUnit, updatedRate ->
                onUpdateProduct(productToEdit.id, updatedName, updatedUnit, updatedRate)
                editingProduct = null
            }
        )
    }
}

@Composable
private fun EditProductDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, unit: String, rateText: String) -> Unit
) {
    var editName by remember(product.id) { mutableStateOf(product.name) }
    var editUnit by remember(product.id) { mutableStateOf(product.unit) }
    var editRate by remember(product.id) {
        mutableStateOf(
            if (product.defaultRate == 0.0) "0"
            else if (product.defaultRate % 1.0 == 0.0) product.defaultRate.toInt().toString()
            else String.format(Locale.US, "%.2f", product.defaultRate)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TealAccent) },
        title = { Text("Edit Product & Price") },
        text = {
            ProvideSafeTextToolbar {
                val dialogFocus = LocalFocusManager.current
                val dialogToolbar = LocalTextToolbar.current
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Product Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_product_name_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editUnit,
                            onValueChange = { editUnit = it },
                            label = { Text("Unit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("edit_product_unit_input")
                        )
                        OutlinedTextField(
                            value = editRate,
                            onValueChange = { editRate = it },
                            label = { Text("Rate (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1.2f).testTag("edit_product_rate_input")
                        )
                    }
                    DisposableEffect(dialogFocus, dialogToolbar) {
                        onDispose {
                            clearFocusAndHideToolbar(dialogFocus, dialogToolbar)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val btnFocus = LocalFocusManager.current
            val btnToolbar = LocalTextToolbar.current
            Button(
                onClick = {
                    clearFocusAndHideToolbar(btnFocus, btnToolbar)
                    onSave(editName, editUnit, editRate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                modifier = Modifier.testTag("save_edited_product_button")
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            val btnFocus = LocalFocusManager.current
            val btnToolbar = LocalTextToolbar.current
            TextButton(onClick = {
                clearFocusAndHideToolbar(btnFocus, btnToolbar)
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

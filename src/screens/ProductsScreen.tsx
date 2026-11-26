import React, { useState } from 'react';
import { ProductEntity, ProductComboFile, UserSession } from '../types';
import { AppDb } from '../storage/db';
import { formatINR } from '../utils/rupeesWords';
import { sound } from '../utils/soundEngine';
import {
  Package,
  Plus,
  Search,
  AlertTriangle,
  Layers,
  Edit,
  Trash2,
  X,
  Check,
} from 'lucide-react';

interface ProductsScreenProps {
  products: ProductEntity[];
  combos: ProductComboFile[];
  user: UserSession;
  onProductsUpdated: () => void;
}

export const ProductsScreen: React.FC<ProductsScreenProps> = ({
  products,
  combos,
  user,
  onProductsUpdated,
}) => {
  const [activeTab, setActiveTab] = useState<'PRODUCTS' | 'COMBOS'>('PRODUCTS');
  const [search, setSearch] = useState('');
  const [selectedFolder, setSelectedFolder] = useState('All');

  // Product Modal State
  const [productModalOpen, setProductModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ProductEntity | null>(null);
  const [pName, setPName] = useState('');
  const [pFolder, setPFolder] = useState('Hikvision');
  const [pUnit, setPUnit] = useState('Nos');
  const [pRate, setPRate] = useState<number>(0);
  const [pPurchase, setPPurchase] = useState<number>(0);
  const [pStock, setPStock] = useState<number>(10);
  const [pMinStock, setPMinStock] = useState<number>(2);
  const [pSku, setPSku] = useState('');

  // Combo Modal State
  const [comboModalOpen, setComboModalOpen] = useState(false);
  const [comboName, setComboName] = useState('');
  const [comboDesc, setComboDesc] = useState('');
  const [comboPrice, setComboPrice] = useState<number>(0);
  const [comboItems, setComboItems] = useState<{ productId: number; qty: number }[]>([]);

  const folders = ['All', 'Hikvision', 'Dahua', 'Power Supply', 'Hard Disk', 'Cable', 'Accessories', 'General'];

  const filteredProducts = products.filter((p) => {
    const matchesSearch =
      p.name.toLowerCase().includes(search.toLowerCase()) ||
      p.sku.toLowerCase().includes(search.toLowerCase()) ||
      p.folder.toLowerCase().includes(search.toLowerCase());
    const matchesFolder = selectedFolder === 'All' || p.folder.toLowerCase() === selectedFolder.toLowerCase();
    return matchesSearch && matchesFolder;
  });

  const openAddProduct = () => {
    sound.playClick();
    setEditingProduct(null);
    setPName('');
    setPFolder('Hikvision');
    setPUnit('Nos');
    setPRate(0);
    setPPurchase(0);
    setPStock(10);
    setPMinStock(2);
    setPSku('');
    setProductModalOpen(true);
  };

  const openEditProduct = (p: ProductEntity) => {
    sound.playClick();
    setEditingProduct(p);
    setPName(p.name);
    setPFolder(p.folder);
    setPUnit(p.unit);
    setPRate(p.defaultRate);
    setPPurchase(p.purchasePrice);
    setPStock(p.stockQty);
    setPMinStock(p.minStockAlert);
    setPSku(p.sku);
    setProductModalOpen(true);
  };

  const handleSaveProduct = (e: React.FormEvent) => {
    e.preventDefault();
    if (!pName.trim()) return;

    const payload: ProductEntity = {
      id: editingProduct ? editingProduct.id : 0,
      name: pName.trim(),
      folder: pFolder,
      unit: pUnit || 'Nos',
      defaultRate: pRate,
      purchasePrice: pPurchase,
      stockQty: pStock,
      minStockAlert: pMinStock,
      sku: pSku.trim(),
      barcode: '',
    };

    AppDb.saveProduct(payload);
    sound.playSuccess();
    setProductModalOpen(false);
    onProductsUpdated();
  };

  const handleDeleteProduct = (id: number) => {
    if (confirm('Delete this product from catalog?')) {
      AppDb.deleteProduct(id);
      sound.playAlert();
      onProductsUpdated();
    }
  };

  // Combo Handlers
  const openAddCombo = () => {
    sound.playClick();
    setComboName('');
    setComboDesc('');
    setComboPrice(0);
    setComboItems([
      { productId: products[0]?.id || 1, qty: 1 },
    ]);
    setComboModalOpen(true);
  };

  const handleSaveCombo = (e: React.FormEvent) => {
    e.preventDefault();
    if (!comboName.trim() || comboItems.length === 0) return;

    const items = comboItems.map((ci) => {
      const prod = products.find((p) => p.id === ci.productId);
      return {
        productId: ci.productId,
        productName: prod?.name || 'Item',
        unit: prod?.unit || 'Nos',
        qty: ci.qty,
        catalogRate: prod?.defaultRate || 0,
        folder: prod?.folder || 'General',
      };
    });

    const payload: ProductComboFile = {
      comboFileId: `combo_${Date.now()}`,
      companyId: 'total_solution',
      name: comboName.trim(),
      description: comboDesc.trim(),
      items,
      comboPrice,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    AppDb.saveCombo(payload);
    sound.playSuccess();
    setComboModalOpen(false);
    onProductsUpdated();
  };

  const handleDeleteCombo = (id: string) => {
    if (confirm('Delete this product combo package?')) {
      AppDb.deleteCombo(id);
      sound.playAlert();
      onProductsUpdated();
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Header */}
      <div className="bg-white p-4 sm:p-5 rounded-2xl shadow-sm border border-slate-200 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-2">
            <Package className="w-5 h-5 text-blue-600" />
            Product & Stock Catalog ({products.length} Products)
          </h2>
          <p className="text-xs text-slate-500">
            CCTV inventory, selling rates, purchase costs, low-stock alerts and bundled combo packages
          </p>
        </div>

        <div className="flex gap-2">
          {activeTab === 'PRODUCTS' ? (
            <button
              onClick={openAddProduct}
              className="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-600/30 flex items-center gap-1.5 transition"
            >
              <Plus className="w-4 h-4" />
              Add Product
            </button>
          ) : (
            <button
              onClick={openAddCombo}
              className="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs rounded-xl shadow-lg shadow-indigo-600/30 flex items-center gap-1.5 transition"
            >
              <Plus className="w-4 h-4" />
              Create Combo Package
            </button>
          )}
        </div>
      </div>

      {/* Tabs: Products vs Combos */}
      <div className="flex gap-2 border-b border-slate-200">
        <button
          onClick={() => {
            sound.playClick();
            setActiveTab('PRODUCTS');
          }}
          className={`pb-3 px-3 text-xs font-bold border-b-2 flex items-center gap-1.5 transition ${
            activeTab === 'PRODUCTS'
              ? 'border-blue-600 text-blue-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Package className="w-4 h-4" />
          Hardware Catalog ({products.length})
        </button>
        <button
          onClick={() => {
            sound.playClick();
            setActiveTab('COMBOS');
          }}
          className={`pb-3 px-3 text-xs font-bold border-b-2 flex items-center gap-1.5 transition ${
            activeTab === 'COMBOS'
              ? 'border-indigo-600 text-indigo-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Layers className="w-4 h-4" />
          Product Combo Files ({combos.length})
        </button>
      </div>

      {activeTab === 'PRODUCTS' ? (
        /* PRODUCTS TAB */
        <div className="space-y-4">
          {/* Search & Folder Filters */}
          <div className="bg-white p-4 rounded-2xl shadow-sm border border-slate-200 flex flex-col sm:flex-row gap-3 justify-between items-stretch sm:items-center">
            <div className="relative flex-1 max-w-md">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
              <input
                type="text"
                placeholder="Search products by model, brand, SKU..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-10 pr-4 py-2 text-xs border border-slate-300 rounded-xl outline-none focus:ring-2 focus:ring-blue-500 font-medium"
              />
            </div>

            <div className="flex gap-1 overflow-x-auto pb-1 sm:pb-0">
              {folders.map((f) => (
                <button
                  key={f}
                  onClick={() => {
                    sound.playClick();
                    setSelectedFolder(f);
                  }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition ${
                    selectedFolder === f
                      ? 'bg-blue-600 text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {f}
                </button>
              ))}
            </div>
          </div>

          {/* Table */}
          <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-100 text-slate-700 font-bold border-b border-slate-200">
                  <tr>
                    <th className="py-3 px-4">Item Name / SKU</th>
                    <th className="py-3 px-4">Category</th>
                    <th className="py-3 px-4 text-right">Selling Rate</th>
                    {user.isAdmin && <th className="py-3 px-4 text-right">Purchase Cost</th>}
                    <th className="py-3 px-4 text-center">Stock Level</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredProducts.map((p) => {
                    const isLowStock = p.minStockAlert > 0 && p.stockQty <= p.minStockAlert;
                    return (
                      <tr key={p.id} className="hover:bg-slate-50/70 transition">
                        <td className="py-3 px-4">
                          <div className="font-bold text-slate-900">{p.name}</div>
                          {p.sku && (
                            <span className="text-[10px] font-mono text-slate-400 block mt-0.5">
                              SKU: {p.sku}
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-4">
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 text-slate-700">
                            {p.folder}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right font-mono font-bold text-slate-900">
                          {formatINR(p.defaultRate)}
                        </td>
                        {user.isAdmin && (
                          <td className="py-3 px-4 text-right font-mono text-slate-500">
                            {formatINR(p.purchasePrice)}
                          </td>
                        )}
                        <td className="py-3 px-4 text-center">
                          <span
                            className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold ${
                              isLowStock
                                ? 'bg-rose-100 text-rose-700'
                                : 'bg-emerald-100 text-emerald-800'
                            }`}
                          >
                            {isLowStock && <AlertTriangle className="w-3 h-3 text-rose-600" />}
                            {p.stockQty} {p.unit}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right">
                          <div className="flex items-center justify-end gap-1">
                            <button
                              onClick={() => openEditProduct(p)}
                              className="p-1.5 text-slate-400 hover:text-blue-600 transition"
                              title="Edit Product"
                            >
                              <Edit className="w-4 h-4" />
                            </button>
                            {user.isAdmin && (
                              <button
                                onClick={() => handleDeleteProduct(p.id)}
                                className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                                title="Delete Product"
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ) : (
        /* COMBOS TAB */
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {combos.map((combo) => (
            <div
              key={combo.comboFileId}
              className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200 hover:border-indigo-400 transition space-y-3.5 flex flex-col justify-between"
            >
              <div>
                <div className="flex justify-between items-start">
                  <div>
                    <h3 className="font-bold text-base text-slate-900">{combo.name}</h3>
                    <p className="text-xs text-slate-500 mt-0.5">{combo.description}</p>
                  </div>
                  {user.isAdmin && (
                    <button
                      onClick={() => handleDeleteCombo(combo.comboFileId)}
                      className="p-1.5 text-slate-400 hover:text-rose-600 transition"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>

                {/* Items in Combo */}
                <div className="mt-3 divide-y divide-slate-100 bg-slate-50 rounded-xl p-3 text-xs">
                  <span className="font-bold text-slate-700 block mb-1">Package Components:</span>
                  {combo.items.map((ci, idx) => (
                    <div key={idx} className="py-1 flex justify-between items-center text-slate-600">
                      <span>• {ci.productName}</span>
                      <span className="font-bold font-mono">
                        {ci.qty} {ci.unit}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="pt-3 border-t flex justify-between items-center">
                <span className="text-xs text-slate-400 font-semibold">Bundled Combo Price:</span>
                <span className="text-lg font-black font-mono text-indigo-700">
                  {formatINR(combo.comboPrice)}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add / Edit Product Modal */}
      {productModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-200">
            <div className="bg-gradient-to-r from-blue-900 to-indigo-900 text-white p-5 flex justify-between items-center">
              <h3 className="font-bold text-base">
                {editingProduct ? 'Edit Catalog Hardware' : 'Add New CCTV Product'}
              </h3>
              <button
                onClick={() => setProductModalOpen(false)}
                className="p-1.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveProduct} className="p-6 space-y-4 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">Product Description / Model *</label>
                  <input
                    type="text"
                    required
                    value={pName}
                    onChange={(e) => setPName(e.target.value)}
                    placeholder="e.g. Hikvision 5MP ColorVu Dome Camera"
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-semibold focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Folder Category</label>
                  <select
                    value={pFolder}
                    onChange={(e) => setPFolder(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none bg-white font-medium"
                  >
                    {folders.filter((f) => f !== 'All').map((f) => (
                      <option key={f} value={f}>
                        {f}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Unit of Measure</label>
                  <input
                    type="text"
                    value={pUnit}
                    onChange={(e) => setPUnit(e.target.value)}
                    placeholder="Nos, Mtr, Coil, Bundle..."
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-medium"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Selling Rate (₹) *</label>
                  <input
                    type="number"
                    step="any"
                    required
                    min="0"
                    value={pRate}
                    onChange={(e) => setPRate(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-mono font-bold"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Purchase Cost (₹)</label>
                  <input
                    type="number"
                    step="any"
                    min="0"
                    value={pPurchase}
                    onChange={(e) => setPPurchase(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-mono"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Current Stock Qty</label>
                  <input
                    type="number"
                    step="any"
                    min="0"
                    value={pStock}
                    onChange={(e) => setPStock(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-bold"
                  />
                </div>

                <div>
                  <label className="block font-bold text-slate-700 mb-1">Min Stock Warning Level</label>
                  <input
                    type="number"
                    step="any"
                    min="0"
                    value={pMinStock}
                    onChange={(e) => setPMinStock(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-bold text-rose-600"
                  />
                </div>

                <div className="col-span-2">
                  <label className="block font-bold text-slate-700 mb-1">SKU / Barcode Code</label>
                  <input
                    type="text"
                    value={pSku}
                    onChange={(e) => setPSku(e.target.value)}
                    placeholder="e.g. HIK-5MP-CV"
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-mono"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setProductModalOpen(false)}
                  className="px-4 py-2 border border-slate-300 text-slate-700 font-semibold rounded-xl hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl flex items-center gap-1.5 shadow-md shadow-blue-600/30"
                >
                  <Check className="w-4 h-4" />
                  Save Product
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Combo Modal */}
      {comboModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-200">
            <div className="bg-gradient-to-r from-indigo-900 to-purple-900 text-white p-5 flex justify-between items-center">
              <h3 className="font-bold text-base">Create Multi-Item Combo Package</h3>
              <button
                onClick={() => setComboModalOpen(false)}
                className="p-1.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveCombo} className="p-6 space-y-4 text-xs">
              <div>
                <label className="block font-bold text-slate-700 mb-1">Package Name *</label>
                <input
                  type="text"
                  required
                  value={comboName}
                  onChange={(e) => setComboName(e.target.value)}
                  placeholder="e.g. 8-Camera Full Resort Surveillance Kit"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-bold"
                />
              </div>

              <div>
                <label className="block font-bold text-slate-700 mb-1">Package Description</label>
                <textarea
                  rows={2}
                  value={comboDesc}
                  onChange={(e) => setComboDesc(e.target.value)}
                  placeholder="Items included, target villa/shop size..."
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none"
                />
              </div>

              <div>
                <label className="block font-bold text-slate-700 mb-1">Bundled Combo Price (₹) *</label>
                <input
                  type="number"
                  step="any"
                  required
                  min="0"
                  value={comboPrice}
                  onChange={(e) => setComboPrice(parseFloat(e.target.value) || 0)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl outline-none font-mono font-bold text-indigo-700"
                />
              </div>

              {/* Components */}
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <label className="font-bold text-slate-700">Included Hardware Items:</label>
                  <button
                    type="button"
                    onClick={() =>
                      setComboItems([...comboItems, { productId: products[0]?.id || 1, qty: 1 }])
                    }
                    className="text-indigo-600 font-bold hover:underline"
                  >
                    + Add Item
                  </button>
                </div>

                {comboItems.map((ci, idx) => (
                  <div key={idx} className="flex items-center gap-2">
                    <select
                      value={ci.productId}
                      onChange={(e) => {
                        const updated = [...comboItems];
                        updated[idx].productId = parseInt(e.target.value, 10);
                        setComboItems(updated);
                      }}
                      className="flex-1 px-2.5 py-1.5 border border-slate-300 rounded-lg text-xs"
                    >
                      {products.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name} (Rate: ₹{p.defaultRate})
                        </option>
                      ))}
                    </select>

                    <input
                      type="number"
                      min="1"
                      value={ci.qty}
                      onChange={(e) => {
                        const updated = [...comboItems];
                        updated[idx].qty = parseFloat(e.target.value) || 1;
                        setComboItems(updated);
                      }}
                      className="w-16 px-2 py-1.5 border border-slate-300 rounded-lg text-xs font-bold text-center"
                    />

                    <button
                      type="button"
                      onClick={() => setComboItems(comboItems.filter((_, i) => i !== idx))}
                      className="text-rose-500 hover:text-rose-700"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setComboModalOpen(false)}
                  className="px-4 py-2 border border-slate-300 text-slate-700 font-semibold rounded-xl hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl flex items-center gap-1.5 shadow-md shadow-indigo-600/30"
                >
                  <Check className="w-4 h-4" />
                  Save Combo File
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

# Hướng dẫn FE tích hợp Layout kho và sức chứa vật lý

## 1. Phạm vi thay đổi

Backend đã chuẩn hóa phần layout theo kích thước thực tế và phần sức chứa tồn kho theo vật lý:

- Kích thước, tọa độ và vị trí của layout/rack/bin dùng `BigDecimal`, đơn vị **mét (m)**.
- `maxWeight` dùng **kg**.
- `maxVolume` dùng **m³**.
- Mỗi SKU có thêm khối lượng và thể tích của **một đơn vị SKU**:
  - `unitWeightKg` — kg / 1 đơn vị SKU.
  - `unitVolumeM3` — m³ / 1 đơn vị SKU.
- Khi nhập hàng, backend tính sức chứa theo SKU và số lượng thực tế, không còn so sánh số lượng với kg hoặc m³.

FE không cần đổi endpoint. Cần đổi cách nhập, lưu và hiển thị dữ liệu theo contract dưới đây.

## 2. Contract layout

### 2.1 Endpoint

Tenant:

```text
GET /api/tenant/warehouses/{warehouseId}/layout
PUT /api/tenant/warehouses/{warehouseId}/layout
```

Owner dùng endpoint tương ứng dưới `/api/owner/warehouses/{warehouseId}/layout`.

### 2.2 Đơn vị và ý nghĩa field

| Field | Đơn vị | Ý nghĩa |
|---|---:|---|
| `width` | m | Chiều rộng của layout/rack/bin |
| `length` | m | Chiều dài của layout/rack/bin |
| `height` | m | Chiều cao của layout/rack/bin |
| `coordinateX` | m | Tọa độ X |
| `coordinateY` | m | Tọa độ Y |
| `positionZ` | m | Tọa độ Z, mặc định `0` nếu nằm trên mặt sàn |
| `rotation` | độ | Chỉ rack hỗ trợ `0`, `90`, `180`, `270` |
| `maxWeight` | kg | Khối lượng tối đa; `null` hoặc `0` nghĩa là không giới hạn |
| `maxVolume` | m³ | Thể tích tối đa; `null` hoặc `0` nghĩa là không giới hạn |

Tọa độ rack tính theo layout. Tọa độ bin tính **tương đối theo rack cha**, không tính trực tiếp theo layout.

### 2.3 Payload mẫu

```json
{
  "width": 30.5,
  "length": 20.25,
  "height": 8.0,
  "racks": [
    {
      "id": "rack-id",
      "name": "Rack A",
      "code": "R-A",
      "maxWeight": 1200.0,
      "maxVolume": 48.0,
      "coordinateX": 1.25,
      "coordinateY": 2.5,
      "positionZ": 0.0,
      "rotation": 0,
      "width": 4.0,
      "length": 3.0,
      "height": 5.0,
      "bins": [
        {
          "id": "bin-id",
          "shelfLevel": 1,
          "name": "Bin A-01",
          "code": "A-01",
          "maxWeight": 250.0,
          "maxVolume": 3.0,
          "coordinateX": 0.2,
          "coordinateY": 0.2,
          "positionZ": 0.5,
          "width": 1.0,
          "length": 1.2,
          "height": 1.0
        }
      ]
    }
  ]
}
```

### 2.4 Các validation bắt buộc

Backend sẽ từ chối request nếu:

- Kích thước hoặc tọa độ không dương/không hợp lệ.
- Rack vượt ra ngoài layout.
- Bin vượt ra ngoài rack cha.
- Rack cùng layout bị chồng lấn.
- Bin cùng một rack bị chồng lấn.
- `maxVolume` lớn hơn thể tích hình học `width × length × height`.
- Mã rack trùng trong cùng layout hoặc mã bin trùng trong cùng rack.
- `rotation` không thuộc `0`, `90`, `180`, `270`.

Không dùng `occupiedPositions` làm nguồn sự thật cho kích thước. Đây chỉ là dữ liệu tương thích cũ để hiển thị các ô lưới thô; geometry thật nằm ở các field mét bên trên.

## 3. Contract SKU và sức chứa vật lý

### 3.1 Endpoint SKU

```text
POST /api/tenant/products/skus
PUT  /api/tenant/products/skus/{id}
GET  /api/tenant/products/skus
GET  /api/tenant/products/skus/{id}
```

Chỉ tenant được tạo SKU mới.

Payload tạo/cập nhật SKU phải có:

```json
{
  "skuCode": "FRIDGE-001",
  "name": "Tủ lạnh",
  "uomId": "uom-id",
  "unitWeightKg": 52.5,
  "unitVolumeM3": 0.85
}
```

`unitWeightKg` và `unitVolumeM3` phải lớn hơn `0`. Hai giá trị này là thuộc tính vật lý của **một đơn vị theo UOM của SKU**, không phải tổng của cả bin.

Ví dụ SKU có UOM `piece`, mỗi tủ lạnh nặng `52.5 kg` và chiếm `0.85 m³` thì gửi đúng hai giá trị trên. Không lấy khối lượng từ `specifications.weight` để tính capacity.

Sau khi SKU đã có stock, không được đổi hai thuộc tính vật lý này. Backend sẽ từ chối cập nhật để tránh làm sai lịch sử capacity.

## 4. Cách backend tính sức chứa

Với mỗi bin và rack, backend tính riêng:

```text
totalWeightKg = SUM(quantity × unitWeightKg)
totalVolumeM3 = SUM(quantity × unitVolumeM3)
```

Một receipt có nhiều dòng cùng rack/bin sẽ được cộng dồn trước khi kiểm tra. Backend kiểm tra cả:

1. Sức chứa của bin.
2. Sức chứa của rack cha.

Kiểm tra được thực hiện khi:

- Tạo receipt inbound.
- Approve receipt inbound.
- Tạo inbound adjustment.

Nếu vượt giới hạn, receipt không được tạo/approve và transaction bị rollback. FE cần hiển thị message lỗi từ API, không tự cộng số lượng để suy đoán kg hoặc m³.

Nếu rack/bin có giới hạn vật lý nhưng SKU cũ thiếu `unitWeightKg` hoặc `unitVolumeM3`, backend sẽ từ chối inbound vì không thể tính chính xác. SKU cần được bổ sung dữ liệu vật lý trước.

## 5. API tồn kho theo từng kho

### 5.1 Product-level overview

```text
GET /api/tenant/inventory/stock/overview?warehouseId={warehouseId}&page=0&size=20
```

Endpoint này trả overview theo **một warehouseId**. Không gọi một lần không có warehouse rồi tự gộp tất cả kho trong FE.

Các field mới trong từng item:

```json
{
  "skuId": "sku-id",
  "skuCode": "FRIDGE-001",
  "name": "Tủ lạnh",
  "unitWeightKg": 52.5,
  "unitVolumeM3": 0.85,
  "totalQuantity": 3,
  "totalWeightKg": 157.5,
  "totalVolumeM3": 2.55,
  "warehouseId": "warehouse-id",
  "warehouseName": "Kho Minh Tâm"
}
```

Để hiển thị cùng một SKU ở nhiều kho, FE gọi endpoint này theo từng `warehouseId`, hoặc dùng endpoint stock detail bên dưới để hiển thị từng vị trí.

### 5.2 Stock detail theo vị trí

```text
GET /api/tenant/inventory/stock?warehouseId={warehouseId}&page=0&size=20
```

Endpoint này dùng khi cần hiển thị stock batch và vị trí rack/bin cụ thể. Không dùng product overview để suy ra vị trí từng bin.

## 6. Việc FE cần điều chỉnh

### 6.1 Layout editor

- Bỏ ép kiểu số nguyên và bỏ `Math.round`/`integerOf` trước khi gửi request.
- Giữ nguyên số thập phân người dùng nhập, ví dụ `1.25`, `3.5`, `0.75`.
- Không dùng các giá trị mặc định kiểu grid như `18`, `8`, `4` nếu chúng không phải kích thước mét thật.
- Gửi đầy đủ `width`, `length`, `height` của layout, rack và bin.
- Không bỏ field `length` khi serialize.
- Bins phải gửi tọa độ local theo rack cha.
- `positionZ` phải là mét; nếu đặt trên sàn thì gửi `0`.
- Khi tenant chỉnh layout, giữ nguyên `id`, danh sách rack/bin hiện có; tenant không được thêm hoặc xóa rack/bin qua API hiện tại.
- Các query parameter kiểu `?width=...&height=...` trên URL không tự cập nhật layout. Muốn lưu kích thước phải gửi `PUT .../{warehouseId}/layout`.

### 6.2 Form tạo SKU

- Thêm input bắt buộc `unitWeightKg` và `unitVolumeM3`.
- Ghi rõ đơn vị ngay cạnh input: `kg / UOM` và `m³ / UOM`.
- Không cho nhập `0` hoặc số âm.
- Không cho sửa hai giá trị này sau khi SKU đã phát sinh stock; nếu API trả lỗi thì hiển thị lý do cho người dùng.

### 6.3 Form inbound

- Chọn warehouse trước, sau đó chọn rack/bin thuộc đúng warehouse/layout.
- Hiển thị cảnh báo nếu SKU chưa có thông tin vật lý.
- Không so sánh `quantity` trực tiếp với `maxWeight` hoặc `maxVolume`.
- Hiển thị lỗi capacity từ backend khi tạo hoặc approve receipt.
- Với màn hình inventory, giữ `warehouseId` trong state/filter; không gộp nhiều warehouse thành một bảng nếu người dùng chưa chọn chế độ tổng hợp.

## 7. Migration và dữ liệu cũ

Backend có các migration:

- `20260815_add_sku_physical_properties.sql`
- `20260815_migrate_layout_geometry_to_meters.sql`
- `20260815_add_layout_scalar_constraints.sql`
- `20260815_add_stock_batch_location_unique_index.sql`

Migration geometry chuyển các giá trị số cũ sang kiểu decimal, nhưng không thể tự đoán liệu giá trị cũ là ô lưới hay mét. Vì vậy trước khi deploy dữ liệu cũ cần được kiểm tra bằng:

```text
ops/maintenance/warehouse_layout_capacity_preflight.sql
```

File này chỉ đọc dữ liệu. Các dòng bất hợp lệ, duplicate stock batch hoặc SKU thiếu thuộc tính vật lý phải được xử lý trước khi dùng capacity thật. Migration unique index sẽ fail có chủ đích nếu phát hiện nhiều active stock batch cho cùng một SKU tại cùng một vị trí.

## 8. Checklist kiểm thử FE

- Tạo layout có số thập phân và reload: giá trị không bị làm tròn.
- Tạo rack vượt layout: API phải trả lỗi.
- Tạo bin vượt rack: API phải trả lỗi.
- Tạo hai bin chồng nhau: API phải trả lỗi.
- SKU 2 kg/đơn vị, nhập 20 đơn vị vào bin max 50 kg: thành công.
- Nhập thêm 6 đơn vị vào cùng bin: bị từ chối nếu tổng vượt 50 kg.
- SKU 0.2 m³/đơn vị, bin max 1 m³: kiểm tra theo m³, không theo quantity.
- Cùng SKU nhập vào hai warehouse: overview của mỗi `warehouseId` chỉ trả số lượng kho đó.
- Receipt inbound nhiều dòng cùng bin: capacity phải tính tổng tất cả dòng.
- SKU thiếu physical properties tại bin/rack có giới hạn: inbound bị từ chối.

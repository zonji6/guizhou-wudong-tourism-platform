CREATE TABLE platform_account (
  id CHAR(36) PRIMARY KEY,
  username VARCHAR(32) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(40) NOT NULL,
  role VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_platform_account_username UNIQUE (username),
  CONSTRAINT ck_platform_account_role CHECK (role IN ('USER', 'ADMIN'))
);

ALTER TABLE merchant
  ADD COLUMN tags VARCHAR(500) NULL AFTER contact_phone,
  ADD COLUMN image_url VARCHAR(500) NULL AFTER tags,
  ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' AFTER demo_data,
  ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER catalog_status,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

ALTER TABLE product
  MODIFY COLUMN price DECIMAL(10,2) NULL,
  ADD COLUMN demo_price_note VARCHAR(200) NOT NULL DEFAULT '本机演示价格，不代表真实经营报价' AFTER price,
  ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' AFTER demo_data,
  ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER catalog_status;

ALTER TABLE food_item
  MODIFY COLUMN price DECIMAL(10,2) NULL,
  ADD COLUMN item_type VARCHAR(16) NOT NULL DEFAULT 'DISH' AFTER description,
  ADD COLUMN demo_price_note VARCHAR(200) NOT NULL DEFAULT '本机演示价格，不代表真实经营报价' AFTER price,
  ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' AFTER demo_data,
  ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER catalog_status;

ALTER TABLE stay_property
  ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' AFTER demo_data,
  ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER catalog_status;

ALTER TABLE room_type
  MODIFY COLUMN price DECIMAL(10,2) NULL,
  ADD COLUMN demo_price_note VARCHAR(200) NOT NULL DEFAULT '本机演示价格，不代表真实经营报价' AFTER price,
  ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' AFTER demo_data,
  ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER catalog_status;

ALTER TABLE place
  ADD COLUMN schematic_x DECIMAL(5,4) NULL AFTER longitude,
  ADD COLUMN schematic_y DECIMAL(5,4) NULL AFTER schematic_x,
  ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' AFTER demo_data,
  ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER catalog_status;

ALTER TABLE community_post
  ADD COLUMN account_id CHAR(36) NULL AFTER visitor_id,
  ADD COLUMN legacy_data BOOLEAN NOT NULL DEFAULT TRUE AFTER demo_data,
  ADD CONSTRAINT fk_community_post_account FOREIGN KEY (account_id) REFERENCES platform_account(id),
  ADD INDEX idx_community_post_account (account_id);

ALTER TABLE product_order
  MODIFY COLUMN visitor_id CHAR(36) NULL,
  ADD COLUMN account_id CHAR(36) NULL AFTER visitor_id,
  ADD COLUMN product_name_snapshot VARCHAR(120) NULL AFTER product_id,
  ADD COLUMN catalog_version_at_order INT NULL AFTER product_name_snapshot,
  ADD COLUMN unit VARCHAR(24) NULL AFTER catalog_version_at_order,
  ADD COLUMN unit_amount DECIMAL(10,2) NULL AFTER unit,
  ADD COLUMN total_amount DECIMAL(10,2) NULL AFTER unit_amount,
  ADD COLUMN currency CHAR(3) NULL AFTER total_amount,
  ADD COLUMN source_draft_id CHAR(36) NULL AFTER source_thread_id,
  ADD CONSTRAINT fk_product_order_account FOREIGN KEY (account_id) REFERENCES platform_account(id),
  ADD INDEX idx_product_order_account (account_id, created_at);

ALTER TABLE food_order
  MODIFY COLUMN visitor_id CHAR(36) NULL,
  MODIFY COLUMN food_item_id CHAR(36) NULL,
  ADD COLUMN account_id CHAR(36) NULL AFTER visitor_id,
  ADD COLUMN merchant_id CHAR(36) NULL AFTER account_id,
  ADD COLUMN merchant_name_snapshot VARCHAR(120) NULL AFTER merchant_id,
  ADD COLUMN total_amount DECIMAL(10,2) NULL AFTER people_count,
  ADD COLUMN currency CHAR(3) NULL AFTER total_amount,
  ADD COLUMN source_draft_id CHAR(36) NULL AFTER source_thread_id,
  ADD CONSTRAINT fk_food_order_account FOREIGN KEY (account_id) REFERENCES platform_account(id),
  ADD UNIQUE INDEX uk_food_order_source_draft (source_draft_id),
  ADD INDEX idx_food_order_account (account_id, created_at),
  ADD INDEX idx_food_order_merchant (merchant_id, created_at);

CREATE TABLE food_order_item (
  id CHAR(36) PRIMARY KEY,
  food_order_id CHAR(36) NOT NULL,
  sequence_no INT NOT NULL,
  food_item_id CHAR(36) NOT NULL,
  food_item_name_snapshot VARCHAR(120) NOT NULL,
  item_type_snapshot VARCHAR(16) NOT NULL,
  catalog_version_at_order INT NOT NULL,
  unit VARCHAR(24) NOT NULL,
  unit_amount DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  line_amount DECIMAL(10,2) NOT NULL,
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_food_order_item_order FOREIGN KEY (food_order_id) REFERENCES food_order(id),
  CONSTRAINT fk_food_order_item_catalog FOREIGN KEY (food_item_id) REFERENCES food_item(id),
  CONSTRAINT uk_food_order_item_sequence UNIQUE (food_order_id, sequence_no)
);

ALTER TABLE stay_booking
  MODIFY COLUMN visitor_id CHAR(36) NULL,
  ADD COLUMN account_id CHAR(36) NULL AFTER visitor_id,
  ADD COLUMN stay_property_id CHAR(36) NULL AFTER account_id,
  ADD COLUMN stay_property_name_snapshot VARCHAR(120) NULL AFTER stay_property_id,
  ADD COLUMN room_type_name_snapshot VARCHAR(120) NULL AFTER room_type_id,
  ADD COLUMN catalog_version_at_order INT NULL AFTER room_type_name_snapshot,
  ADD COLUMN check_out_date DATE NULL AFTER check_in_date,
  ADD COLUMN nights INT NULL AFTER check_out_date,
  ADD COLUMN room_count INT NULL AFTER nights,
  ADD COLUMN max_guests_per_room_at_order INT NULL AFTER people_count,
  ADD COLUMN unit VARCHAR(24) NULL AFTER max_guests_per_room_at_order,
  ADD COLUMN unit_amount DECIMAL(10,2) NULL AFTER unit,
  ADD COLUMN total_amount DECIMAL(10,2) NULL AFTER unit_amount,
  ADD COLUMN currency CHAR(3) NULL AFTER total_amount,
  ADD COLUMN source_draft_id CHAR(36) NULL AFTER source_thread_id,
  ADD CONSTRAINT fk_stay_booking_account FOREIGN KEY (account_id) REFERENCES platform_account(id),
  ADD UNIQUE INDEX uk_stay_booking_source_draft (source_draft_id),
  ADD INDEX idx_stay_booking_account (account_id, created_at);

CREATE TABLE saved_itinerary (
  id CHAR(36) PRIMARY KEY,
  account_id CHAR(36) NOT NULL,
  version INT NOT NULL DEFAULT 1,
  content_json JSON NOT NULL,
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  source_thread_id CHAR(36) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_saved_itinerary_account FOREIGN KEY (account_id) REFERENCES platform_account(id),
  INDEX idx_saved_itinerary_account (account_id, updated_at)
);

CREATE TABLE food_draft (
  id CHAR(36) PRIMARY KEY,
  account_id CHAR(36) NOT NULL,
  version INT NOT NULL DEFAULT 1,
  state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  content_json JSON NOT NULL,
  source_thread_id CHAR(36) NULL,
  linked_order_id CHAR(36) NULL,
  submitted_at DATETIME NULL,
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_food_draft_account FOREIGN KEY (account_id) REFERENCES platform_account(id),
  CONSTRAINT uk_food_draft_linked_order UNIQUE (linked_order_id),
  INDEX idx_food_draft_account (account_id, state, updated_at)
);

CREATE TABLE stay_draft (
  id CHAR(36) PRIMARY KEY,
  account_id CHAR(36) NOT NULL,
  version INT NOT NULL DEFAULT 1,
  state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  content_json JSON NOT NULL,
  source_thread_id CHAR(36) NULL,
  linked_order_id CHAR(36) NULL,
  submitted_at DATETIME NULL,
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_stay_draft_account FOREIGN KEY (account_id) REFERENCES platform_account(id),
  CONSTRAINT uk_stay_draft_linked_order UNIQUE (linked_order_id),
  INDEX idx_stay_draft_account (account_id, state, updated_at)
);

CREATE TABLE operation_receipt (
  account_id CHAR(36) NOT NULL,
  operation_type VARCHAR(48) NOT NULL,
  request_key CHAR(36) NOT NULL,
  request_digest CHAR(71) NOT NULL,
  requested_resource_type VARCHAR(32) NOT NULL,
  requested_resource_id CHAR(36) NULL,
  state VARCHAR(16) NOT NULL,
  result_resource_type VARCHAR(32) NULL,
  result_resource_id CHAR(36) NULL,
  committed_version INT NULL,
  submitted_draft_id CHAR(36) NULL,
  submitted_draft_version INT NULL,
  terminal_reason VARCHAR(48) NULL,
  reserved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  committed_at DATETIME NULL,
  terminal_at DATETIME NULL,
  PRIMARY KEY (account_id, operation_type, request_key),
  CONSTRAINT fk_operation_receipt_account FOREIGN KEY (account_id) REFERENCES platform_account(id),
  INDEX idx_operation_receipt_result (result_resource_type, result_resource_id)
);

ALTER TABLE knowledge_document
  MODIFY COLUMN title VARCHAR(180) NULL,
  MODIFY COLUMN content TEXT NULL,
  ADD COLUMN candidate_code VARCHAR(40) NULL AFTER id,
  ADD COLUMN row_version INT NOT NULL DEFAULT 1 AFTER demo_data,
  ADD COLUMN draft_revision INT NOT NULL DEFAULT 1 AFTER row_version,
  ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER draft_revision,
  ADD COLUMN draft_json JSON NULL AFTER visibility,
  ADD COLUMN live_snapshot JSON NULL AFTER draft_json,
  ADD COLUMN live_snapshot_hash CHAR(71) NULL AFTER live_snapshot,
  ADD COLUMN active_task_id CHAR(36) NULL AFTER live_snapshot_hash,
  ADD COLUMN current_task_id CHAR(36) NULL AFTER active_task_id,
  ADD COLUMN published_at DATETIME NULL AFTER current_task_id,
  ADD UNIQUE INDEX uk_knowledge_candidate_code (candidate_code),
  ADD INDEX idx_knowledge_visibility (visibility, published_at);

CREATE TABLE knowledge_source (
  id CHAR(36) PRIMARY KEY,
  source_version INT NOT NULL DEFAULT 1,
  source_key VARCHAR(40) NOT NULL,
  title VARCHAR(200) NOT NULL,
  url VARCHAR(2048) NULL,
  publisher VARCHAR(200) NULL,
  source_kind VARCHAR(80) NOT NULL,
  publication_date_text VARCHAR(80) NULL,
  read_at DATETIME NULL,
  locator VARCHAR(300) NULL,
  read_status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_knowledge_source_key UNIQUE (source_key)
);

CREATE TABLE knowledge_publish_task (
  id CHAR(36) PRIMARY KEY,
  document_id CHAR(36) NOT NULL,
  requested_by CHAR(36) NOT NULL,
  request_key CHAR(36) NOT NULL,
  request_hash CHAR(71) NOT NULL,
  status VARCHAR(16) NOT NULL,
  draft_revision INT NULL,
  input_snapshot JSON NULL,
  snapshot_hash CHAR(71) NULL,
  retrieval_mode VARCHAR(24) NULL,
  index_config JSON NULL,
  config_hash CHAR(71) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at DATETIME NULL,
  deadline_at DATETIME NOT NULL,
  finished_at DATETIME NULL,
  build_receipt JSON NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(200) NULL,
  CONSTRAINT uk_knowledge_publish_request UNIQUE (requested_by, request_key),
  INDEX idx_knowledge_publish_document (document_id, created_at),
  INDEX idx_knowledge_publish_status (status, deadline_at)
);

CREATE TABLE agent_run_summary_v3 (
  thread_id CHAR(36) NOT NULL,
  run_id CHAR(36) NOT NULL,
  last_sequence INT NOT NULL,
  last_digest CHAR(71) NOT NULL,
  run_state VARCHAR(24) NOT NULL,
  agent_type VARCHAR(32) NOT NULL,
  retrieval_mode VARCHAR(24) NOT NULL,
  started_at DATETIME NOT NULL,
  finished_at DATETIME NULL,
  knowledge_dependencies_json JSON NOT NULL,
  candidate_refs_json JSON NOT NULL,
  error_code VARCHAR(64) NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (thread_id, run_id)
);

UPDATE merchant
SET catalog_status = 'PUBLISHED', verification_status = 'UNVERIFIED', version = 1
WHERE id IN ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002');

INSERT INTO product (
  id, merchant_id, name, description, price, demo_price_note, pickup_point, tags,
  image_url, demo_data, verification_status, catalog_status, version)
VALUES (
  '40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
  '乌东云雾茶演示礼盒', '用于本机演示的乌东茶主题伴手礼。', 68.00,
  '本机演示价格，不代表真实经营报价', '乌东云栖茶园演示取货点', '茶与伴手礼,演示数据',
  NULL, TRUE, 'UNVERIFIED', 'PUBLISHED', 1);

INSERT INTO food_item (
  id, merchant_id, name, description, item_type, price, demo_price_note, visit_time_text,
  tags, image_url, demo_data, verification_status, catalog_status, version)
VALUES
  ('41000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
   '苗家长桌宴演示套餐', '本机演示用到店餐食套餐。', 'SET', 98.00,
   '本机演示价格，不代表真实经营报价', '建议提前确认到店时间', '正餐,体验,演示数据',
   NULL, TRUE, 'UNVERIFIED', 'PUBLISHED', 1),
  ('41000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
   '乌东茶点演示份', '本机演示用茶点。', 'DISH', 28.00,
   '本机演示价格，不代表真实经营报价', '建议提前确认到店时间', '茶点,演示数据',
   NULL, TRUE, 'UNVERIFIED', 'PUBLISHED', 1),
  ('41000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002',
   '山泉茶饮演示杯', '本机演示用茶饮。', 'DRINK', 18.00,
   '本机演示价格，不代表真实经营报价', '建议提前确认到店时间', '茶点,演示数据',
   NULL, TRUE, 'UNVERIFIED', 'PUBLISHED', 1);

INSERT INTO stay_property (
  id, merchant_id, name, description, location_text, tags, image_url, demo_data,
  verification_status, catalog_status, version)
VALUES (
  '42000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
  '乌东苗寨山景民宿', '本机演示住宿主体，不代表真实房态。', '乌东苗寨演示位置',
  '民宿,苗寨,山景,演示数据', NULL, TRUE, 'UNVERIFIED', 'PUBLISHED', 1);

INSERT INTO room_type (
  id, stay_property_id, name, description, max_guests, price, demo_price_note,
  image_url, demo_data, verification_status, catalog_status, version)
VALUES (
  '42100000-0000-0000-0000-000000000001', '42000000-0000-0000-0000-000000000001',
  '山景双人房演示房型', '本机演示房型，无实时房态。', 2, 298.00,
  '本机演示价格，不代表真实经营报价', NULL, TRUE, 'UNVERIFIED', 'PUBLISHED', 1);

INSERT INTO place (
  id, name, category, description, latitude, longitude, schematic_x, schematic_y,
  tags, image_url, demo_data, verification_status, catalog_status, version)
VALUES
  ('43000000-0000-0000-0000-000000000001', '乌东茶园演示节点', 'TEA_GARDEN',
   '水彩示意地图节点，不代表真实经纬度。', NULL, NULL, 0.2200, 0.3200,
   '茶园,示意地图,演示数据', NULL, TRUE, 'UNVERIFIED', 'PUBLISHED', 1),
  ('43000000-0000-0000-0000-000000000002', '乌东苗寨演示节点', 'VILLAGE',
   '水彩示意地图节点，不代表真实经纬度。', NULL, NULL, 0.5800, 0.5000,
   '苗寨,示意地图,演示数据', NULL, TRUE, 'UNVERIFIED', 'PUBLISHED', 1),
  ('43000000-0000-0000-0000-000000000003', '溪畔慢行演示节点', 'SCENIC',
   '水彩示意地图节点，不代表真实经纬度。', NULL, NULL, 0.8200, 0.7200,
   '溪流,示意地图,演示数据', NULL, TRUE, 'UNVERIFIED', 'PUBLISHED', 1);

UPDATE knowledge_document
SET draft_json = JSON_OBJECT(
      'title', title,
      'content', content,
      'tags', CASE WHEN tags IS NULL OR tags = '' THEN JSON_ARRAY() ELSE JSON_ARRAY(tags) END,
      'region', '乌东',
      'periodText', NULL,
      'evidenceCategory', '本机演示资料',
      'usageLimitations', JSON_ARRAY('仅用于本机演示'),
      'demoData', demo_data,
      'sourceSnapshots', JSON_ARRAY()
    ),
    visibility = 'DRAFT',
    published = FALSE,
    index_status = 'DRAFT';

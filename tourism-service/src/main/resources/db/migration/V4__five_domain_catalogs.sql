ALTER TABLE merchant
  ADD COLUMN demo_data BOOLEAN NOT NULL DEFAULT TRUE AFTER contact_phone,
  ADD COLUMN catalog_status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED' AFTER demo_data,
  ADD INDEX idx_merchant_catalog_status (catalog_status);

ALTER TABLE community_post
  MODIFY COLUMN title VARCHAR(180) NULL,
  ADD COLUMN visitor_id CHAR(36) NULL AFTER id,
  ADD COLUMN post_type VARCHAR(32) NOT NULL DEFAULT 'MOMENT' AFTER author_name,
  ADD COLUMN route_summary VARCHAR(500) NULL AFTER post_type,
  ADD COLUMN route_nodes_json JSON NULL AFTER route_summary,
  ADD COLUMN published BOOLEAN NOT NULL DEFAULT TRUE AFTER demo_data,
  ADD INDEX idx_post_visitor (visitor_id),
  ADD INDEX idx_post_type_published (post_type, published);

CREATE TABLE product (
  id CHAR(36) PRIMARY KEY,
  merchant_id CHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  pickup_point VARCHAR(160) NOT NULL,
  tags VARCHAR(500),
  image_url VARCHAR(500),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  catalog_status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_product_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  INDEX idx_product_catalog_status (catalog_status),
  INDEX idx_product_merchant (merchant_id)
);

CREATE TABLE food_item (
  id CHAR(36) PRIMARY KEY,
  merchant_id CHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  visit_time_text VARCHAR(160),
  tags VARCHAR(500),
  image_url VARCHAR(500),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  catalog_status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_food_item_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  INDEX idx_food_item_catalog_status (catalog_status),
  INDEX idx_food_item_merchant (merchant_id)
);

CREATE TABLE stay_property (
  id CHAR(36) PRIMARY KEY,
  merchant_id CHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  location_text VARCHAR(160),
  tags VARCHAR(500),
  image_url VARCHAR(500),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  catalog_status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_stay_property_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  INDEX idx_stay_property_catalog_status (catalog_status),
  INDEX idx_stay_property_merchant (merchant_id)
);

CREATE TABLE room_type (
  id CHAR(36) PRIMARY KEY,
  stay_property_id CHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  max_guests INT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  image_url VARCHAR(500),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  catalog_status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_room_type_stay_property FOREIGN KEY (stay_property_id) REFERENCES stay_property(id),
  INDEX idx_room_type_catalog_status (catalog_status),
  INDEX idx_room_type_stay_property (stay_property_id)
);

CREATE TABLE place (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(64) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  tags VARCHAR(500),
  image_url VARCHAR(500),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  catalog_status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_place_category_status (category, catalog_status)
);

CREATE TABLE product_order (
  id CHAR(36) PRIMARY KEY,
  visitor_id CHAR(36) NOT NULL,
  product_id CHAR(36) NOT NULL,
  quantity INT NOT NULL,
  pickup_point VARCHAR(160) NOT NULL,
  contact_name VARCHAR(80) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  note VARCHAR(500),
  status VARCHAR(32) NOT NULL,
  source_thread_id VARCHAR(100),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_product_order_product FOREIGN KEY (product_id) REFERENCES product(id),
  INDEX idx_product_order_visitor (visitor_id, created_at),
  INDEX idx_product_order_status (status),
  INDEX idx_product_order_thread (source_thread_id)
);

CREATE TABLE food_order (
  id CHAR(36) PRIMARY KEY,
  visitor_id CHAR(36) NOT NULL,
  food_item_id CHAR(36) NOT NULL,
  visit_at DATETIME NOT NULL,
  people_count INT NOT NULL,
  contact_name VARCHAR(80) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  note VARCHAR(500),
  status VARCHAR(32) NOT NULL,
  source_thread_id VARCHAR(100),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_food_order_food_item FOREIGN KEY (food_item_id) REFERENCES food_item(id),
  INDEX idx_food_order_visitor (visitor_id, created_at),
  INDEX idx_food_order_status (status),
  INDEX idx_food_order_thread (source_thread_id)
);

CREATE TABLE stay_booking (
  id CHAR(36) PRIMARY KEY,
  visitor_id CHAR(36) NOT NULL,
  room_type_id CHAR(36) NOT NULL,
  check_in_date DATE NOT NULL,
  people_count INT NOT NULL,
  contact_name VARCHAR(80) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  note VARCHAR(500),
  status VARCHAR(32) NOT NULL,
  source_thread_id VARCHAR(100),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_stay_booking_room_type FOREIGN KEY (room_type_id) REFERENCES room_type(id),
  INDEX idx_stay_booking_visitor (visitor_id, created_at),
  INDEX idx_stay_booking_status (status),
  INDEX idx_stay_booking_thread (source_thread_id)
);

CREATE TABLE agent_run_summary (
  id CHAR(36) PRIMARY KEY,
  thread_id VARCHAR(100) NOT NULL,
  node_name VARCHAR(80) NOT NULL,
  tool_category VARCHAR(80),
  source_titles_json JSON NOT NULL,
  duration_ms BIGINT NOT NULL,
  final_status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_agent_run_thread_created (thread_id, created_at),
  INDEX idx_agent_run_final_status (final_status)
);

CREATE TABLE merchant (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NOT NULL,
  contact_phone VARCHAR(32),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE service_resource (
  id CHAR(36) PRIMARY KEY,
  merchant_id CHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(32) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  duration_text VARCHAR(80),
  location_text VARCHAR(160),
  tags VARCHAR(500),
  image_url VARCHAR(500),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_service_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  INDEX idx_service_category (category),
  INDEX idx_service_active (active)
);

CREATE TABLE community_post (
  id CHAR(36) PRIMARY KEY,
  title VARCHAR(180) NOT NULL,
  content TEXT NOT NULL,
  author_name VARCHAR(80) NOT NULL,
  cover_url VARCHAR(500),
  tags VARCHAR(500),
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_post_published (published_at)
);

CREATE TABLE knowledge_document (
  id CHAR(36) PRIMARY KEY,
  title VARCHAR(180) NOT NULL,
  content TEXT NOT NULL,
  source_type VARCHAR(40) NOT NULL DEFAULT 'DEMO',
  tags VARCHAR(500),
  published BOOLEAN NOT NULL DEFAULT TRUE,
  index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  demo_data BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_knowledge_published (published)
);

CREATE TABLE booking (
  id CHAR(36) PRIMARY KEY,
  service_id CHAR(36) NOT NULL,
  travel_date DATE NOT NULL,
  people_count INT NOT NULL,
  contact_name VARCHAR(80) NOT NULL,
  contact_phone VARCHAR(32) NOT NULL,
  note VARCHAR(500),
  status VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  thread_id VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_booking_service FOREIGN KEY (service_id) REFERENCES service_resource(id),
  INDEX idx_booking_status (status),
  INDEX idx_booking_thread (thread_id)
);

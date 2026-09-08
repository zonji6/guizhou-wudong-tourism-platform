UPDATE merchant
SET name = REPLACE(name, '乌冬', '乌东'),
    description = REPLACE(description, '乌冬', '乌东');

UPDATE service_resource
SET name = REPLACE(name, '乌冬', '乌东'),
    description = REPLACE(description, '乌冬', '乌东'),
    location_text = REPLACE(location_text, '乌冬', '乌东');

UPDATE community_post
SET title = REPLACE(title, '乌冬', '乌东'),
    content = REPLACE(content, '乌冬', '乌东'),
    author_name = REPLACE(author_name, '乌冬', '乌东');

UPDATE knowledge_document
SET title = REPLACE(title, '乌冬', '乌东'),
    content = REPLACE(content, '乌冬', '乌东');

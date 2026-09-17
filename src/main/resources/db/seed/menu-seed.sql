-- 메뉴 시드 (REQ-MN-006, menu_seed.py로 생성 — 직접 고치지 말고 menu_seed.py를 고친 뒤 재생성)
-- 실행 전: SET @coffeul_store_id = <실제 store.id>;  (범석관 · 뉴밀레니엄관 각각 한 번씩)
-- Flyway 마이그레이션이 아님 — school · merchant · store가 생긴 뒤 매장마다 수동 실행

-- 카테고리 (7개)
INSERT INTO `category` (`store_id`, `name`, `sort_order`) VALUES (@coffeul_store_id, 'HOT COFFEE', 0);
SET @cat_0 = LAST_INSERT_ID();
INSERT INTO `category` (`store_id`, `name`, `sort_order`) VALUES (@coffeul_store_id, 'HOT 티', 1);
SET @cat_1 = LAST_INSERT_ID();
INSERT INTO `category` (`store_id`, `name`, `sort_order`) VALUES (@coffeul_store_id, 'ICE COFFEE', 2);
SET @cat_2 = LAST_INSERT_ID();
INSERT INTO `category` (`store_id`, `name`, `sort_order`) VALUES (@coffeul_store_id, 'ICE 티', 3);
SET @cat_3 = LAST_INSERT_ID();
INSERT INTO `category` (`store_id`, `name`, `sort_order`) VALUES (@coffeul_store_id, '라떼', 4);
SET @cat_4 = LAST_INSERT_ID();
INSERT INTO `category` (`store_id`, `name`, `sort_order`) VALUES (@coffeul_store_id, '에이드', 5);
SET @cat_5 = LAST_INSERT_ID();
INSERT INTO `category` (`store_id`, `name`, `sort_order`) VALUES (@coffeul_store_id, '프라페', 6);
SET @cat_6 = LAST_INSERT_ID();

-- 메뉴 (41개) + 카테고리 연결 + 온도 · 사이즈 옵션
INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '고구마라떼', 3800, FALSE, 0);
SET @item_0 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_0, @cat_4, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_0, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '곡물라떼', 3500, FALSE, 1);
SET @item_1 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_1, @cat_4, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_1, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_1, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '그린티프라페', 4000, FALSE, 2);
SET @item_2 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_2, @cat_6, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_2, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '녹차라떼', 3500, FALSE, 3);
SET @item_3 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_3, @cat_4, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_3, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_3, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '딸기라떼', 3800, FALSE, 4);
SET @item_4 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_4, @cat_4, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_4, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, TRUE, 0);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '레몬에이드', 3500, FALSE, 5);
SET @item_5 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_5, @cat_5, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_5, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '레몬차', 3300, FALSE, 6);
SET @item_6 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_6, @cat_1, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_6, @cat_3, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_6, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '미초', 3000, FALSE, 7);
SET @item_7 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_7, @cat_3, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_7, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, TRUE, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_7, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '미초에이드', 3500, FALSE, 8);
SET @item_8 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_8, @cat_5, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_8, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '민트초코라떼', 3500, FALSE, 9);
SET @item_9 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_9, @cat_4, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_9, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_9, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '밀크티(홍차라떼)', 3500, FALSE, 10);
SET @item_10 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_10, @cat_4, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_10, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_10, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '바닐라라떼', 2800, FALSE, 11);
SET @item_11 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_11, @cat_0, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_11, @cat_2, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_11, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_11, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '복숭아프라페', 4200, FALSE, 12);
SET @item_12 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_12, @cat_6, 0);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '블랙티(얼그레이)', 3000, FALSE, 13);
SET @item_13 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_13, @cat_1, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_13, @cat_3, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_13, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '생강차', 3300, FALSE, 14);
SET @item_14 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_14, @cat_1, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_14, @cat_3, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_14, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '아메리카노', 1500, TRUE, 15);
SET @item_15 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_15, @cat_0, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_15, @cat_2, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_15, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_15, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '아샷추', 2500, FALSE, 16);
SET @item_16 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_16, @cat_3, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_16, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, TRUE, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_16, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '아이스티(복숭아)', 2000, FALSE, 17);
SET @item_17 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_17, @cat_3, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_17, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, TRUE, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_17, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '에스프레소', 2000, FALSE, 18);
SET @item_18 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_18, @cat_0, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_18, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '연유라떼', 3000, FALSE, 19);
SET @item_19 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_19, @cat_0, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_19, @cat_2, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_19, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_19, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '요거트프라페(딸기)', 4000, FALSE, 20);
SET @item_20 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_20, @cat_6, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_20, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '요거트프라페(블루베리)', 4000, FALSE, 21);
SET @item_21 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_21, @cat_6, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_21, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '요거트프라페(플레인)', 4000, FALSE, 22);
SET @item_22 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_22, @cat_6, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_22, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '유자차', 3300, FALSE, 23);
SET @item_23 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_23, @cat_1, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_23, @cat_3, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_23, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '자몽에이드', 3500, FALSE, 24);
SET @item_24 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_24, @cat_5, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_24, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '자몽차', 3300, FALSE, 25);
SET @item_25 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_25, @cat_1, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_25, @cat_3, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_25, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '청포도', 3300, FALSE, 26);
SET @item_26 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_26, @cat_3, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_26, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, TRUE, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_26, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '청포도에이드', 3500, FALSE, 27);
SET @item_27 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_27, @cat_5, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_27, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '초코라떼', 3000, FALSE, 28);
SET @item_28 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_28, @cat_4, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_28, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_28, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '초코프라페', 4000, FALSE, 29);
SET @item_29 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_29, @cat_6, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_29, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '카라멜마끼아또', 3000, FALSE, 30);
SET @item_30 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_30, @cat_0, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_30, @cat_2, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_30, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_30, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '카페라떼', 2500, FALSE, 31);
SET @item_31 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_31, @cat_0, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_31, @cat_2, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_31, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_31, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '카페모카', 3000, FALSE, 32);
SET @item_32 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_32, @cat_0, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_32, @cat_2, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_32, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_32, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '커피프라페(모카)', 4000, FALSE, 33);
SET @item_33 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_33, @cat_6, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_33, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '커피프라페(에스프레소)', 4000, FALSE, 34);
SET @item_34 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_34, @cat_6, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_34, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '커피프라페(카라멜)', 4000, FALSE, 35);
SET @item_35 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_35, @cat_6, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_35, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1500, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '콜드브루 디카페인', 3500, FALSE, 36);
SET @item_36 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_36, @cat_2, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_36, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, TRUE, 0);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '토피넛라떼', 3500, FALSE, 37);
SET @item_37 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_37, @cat_4, 0);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_37, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_37, '사이즈', 'SIZE', TRUE, 1, 1, 1);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'R', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'L', 1000, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '허브티(캐모마일)', 3000, FALSE, 38);
SET @item_38 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_38, @cat_1, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_38, @cat_3, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_38, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '허브티(페퍼민트)', 3000, FALSE, 39);
SET @item_39 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_39, @cat_1, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_39, @cat_3, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_39, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);

INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_event`, `sort_order`) VALUES (@coffeul_store_id, '히비스커스티', 3000, FALSE, 40);
SET @item_40 = LAST_INSERT_ID();
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_40, @cat_1, 0);
INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`, `sort_order`) VALUES (@item_40, @cat_3, 1);
INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, `min_select`, `max_select`, `sort_order`) VALUES (@item_40, '온도', 'TEMPERATURE', TRUE, 1, 1, 0);
SET @og = LAST_INSERT_ID();
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'HOT', 0, TRUE, 0);
INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`, `sort_order`) VALUES (@og, 'ICE', 0, FALSE, 1);

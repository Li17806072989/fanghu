USE fang_hu;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO fh_article_category(id,name,sort_no,enabled) VALUES
(1,'政策与行业动态',10,1),
(2,'装备技术',20,1),
(3,'市场与采购',30,1),
(4,'任务应用',40,1)
ON DUPLICATE KEY UPDATE name=VALUES(name),sort_no=VALUES(sort_no),enabled=VALUES(enabled);

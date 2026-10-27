WITH category_rule(priority, pattern, category_id) AS (
  VALUES
    (1, '(restaurant|cafe|coffee|chicken|mcdonald|kfc|starbucks|tealive|familymart|mixue|foodpanda|grabfood|bakery|kitchen|kopitiam|sushi|pizza|burger|mamak)', '00000000-0000-0000-0000-000000000001'::uuid),
    (2, '(99 speedmart|lotus|jaya grocer|aeon|mydin|supermarket|grocery)', '00000000-0000-0000-0000-000000000002'::uuid),
    (3, '(grab|rapid kl|petron|shell|toll|parking|airasia|ktm)', '00000000-0000-0000-0000-000000000003'::uuid),
    (4, '(unifi|tnb|maxis|celcom|digi|u mobile|water bill)', '00000000-0000-0000-0000-000000000004'::uuid),
    (5, '(shopee|lazada|taobao|alipay|temu|uniqlo|mr diy|eco shop)', '00000000-0000-0000-0000-000000000005'::uuid),
    (6, '(guardian|watsons|pharmacy|clinic|hospital)', '00000000-0000-0000-0000-000000000006'::uuid),
    (7, '(university|college|udemy|coursera|bookstore)', '00000000-0000-0000-0000-000000000007'::uuid),
    (8, '(netflix|spotify|cinema|gsc|tgv|steam)', '00000000-0000-0000-0000-000000000008'::uuid)
), matched_transaction AS (
  SELECT DISTINCT ON (transaction.id) transaction.id, rule.category_id
  FROM finance_transaction transaction
  JOIN category_rule rule ON lower(transaction.merchant) ~ rule.pattern
  WHERE transaction.status = 'CONFIRMED'
    AND transaction.kind = 'EXPENSE'
    AND transaction.category_id = '00000000-0000-0000-0000-000000000011'::uuid
  ORDER BY transaction.id, rule.priority
)
UPDATE finance_transaction transaction
SET category_id = matched.category_id,
    categorization_confidence = 0.850,
    categorization_source = 'Seeded merchant keyword',
    updated_at = CURRENT_TIMESTAMP
FROM matched_transaction matched
WHERE transaction.id = matched.id;

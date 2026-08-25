INSERT INTO org_campus (code, name, city)
SELECT 'RICHMOND', 'Richmond', 'London'
WHERE NOT EXISTS (SELECT 1 FROM org_campus WHERE code = 'RICHMOND');

INSERT INTO org_campus (code, name, city)
SELECT 'KINGSTON', 'Kingston', 'Kingston upon Thames'
WHERE NOT EXISTS (SELECT 1 FROM org_campus WHERE code = 'KINGSTON');

INSERT INTO org_campus (code, name, city)
SELECT 'PUTNEY', 'Putney', 'London'
WHERE NOT EXISTS (SELECT 1 FROM org_campus WHERE code = 'PUTNEY');

INSERT INTO org_campus (code, name, city)
SELECT 'LEICESTER_SQUARE', 'Leicester Square', 'London'
WHERE NOT EXISTS (SELECT 1 FROM org_campus WHERE code = 'LEICESTER_SQUARE');

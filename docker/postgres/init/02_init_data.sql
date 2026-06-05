\c agv_dispatch;

INSERT INTO agv_map_node (id, node_code, node_name, x_coord, y_coord, node_type, is_charging_station, is_transfer_station, connected_nodes, remark) VALUES
('node001', 'A01', 'A区入口', 0, 0, 'STORAGE', false, false, 'A02,B01', '起点区域'),
('node002', 'A02', 'A区通道1', 5, 0, 'PATH', false, false, 'A01,A03,B02', ''),
('node003', 'A03', 'A区通道2', 10, 0, 'PATH', false, false, 'A02,A04,B03', ''),
('node004', 'A04', 'A区出口', 15, 0, 'STORAGE', false, true, 'A03,B04', '可换乘'),
('node005', 'B01', 'B区通道1', 0, 5, 'PATH', false, false, 'A01,B02,C01', ''),
('node006', 'B02', 'B区通道2', 5, 5, 'PATH', false, false, 'A02,B01,B03,C02', ''),
('node007', 'B03', 'B区通道3', 10, 5, 'PATH', false, false, 'A03,B02,B04,C03', ''),
('node008', 'B04', 'B区通道4', 15, 5, 'PATH', false, false, 'A04,B03,C04', ''),
('node009', 'C01', 'C区通道1', 0, 10, 'PATH', true, false, 'B01,C02', '充电站'),
('node010', 'C02', 'C区通道2', 5, 10, 'PATH', false, false, 'B02,C01,C03', ''),
('node011', 'C03', 'C区通道3', 10, 10, 'PATH', false, false, 'B03,C02,C04', ''),
('node012', 'C04', 'C区出口', 15, 10, 'STORAGE', false, true, 'B04,C03', '可换乘'),
('node013', 'D01', 'D区入口', 0, 15, 'STORAGE', true, false, 'C01,D02', '充电站'),
('node014', 'D02', 'D区拣选区', 7.5, 15, 'PICKING', false, false, 'D01,D03', ''),
('node015', 'D03', 'D区出口', 15, 15, 'STORAGE', false, true, 'D02', '终点区域')
ON CONFLICT (node_code) DO NOTHING;

INSERT INTO agv_robot (id, agv_no, name, status, current_position, battery_level, x_coord, y_coord, model, max_load, ip_address) VALUES
('agv001', 'AGV001', '搬运车1号', 0, 'A01', 95.0, 0, 0, 'Standard-001', 1000.0, '192.168.1.101'),
('agv002', 'AGV002', '搬运车2号', 0, 'C01', 88.5, 0, 10, 'Standard-001', 1000.0, '192.168.1.102'),
('agv003', 'AGV003', '搬运车3号', 0, 'A04', 76.2, 15, 0, 'Standard-001', 1000.0, '192.168.1.103'),
('agv004', 'AGV004', '拣选车1号', 0, 'D01', 92.0, 0, 15, 'Picking-001', 500.0, '192.168.1.104'),
('agv005', 'AGV005', '搬运车4号', 0, 'C04', 85.0, 15, 10, 'Standard-001', 1000.0, '192.168.1.105')
ON CONFLICT (agv_no) DO NOTHING;

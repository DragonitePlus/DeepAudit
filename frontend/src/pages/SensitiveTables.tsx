import React, { useEffect, useState } from 'react';
import { Table, Button, Card, Tag, Modal, Form, Input, InputNumber, Select, message, Popconfirm, Typography } from 'antd';
import { Plus, Edit, Trash2, Database, AlertTriangle } from 'lucide-react';
import { sensitiveTableService } from '../services/api';
import { SysSensitiveTable } from '../types';

const { Title, Text } = Typography;
const { Option } = Select;

const SensitiveTables: React.FC = () => {
  const [tables, setTables] = useState<SysSensitiveTable[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTable, setEditingTable] = useState<SysSensitiveTable | null>(null);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const fetchTables = async () => {
    setLoading(true);
    try {
      const data = await sensitiveTableService.getAll();
      setTables(data);
    } catch (error) {
      message.error('Failed to load sensitive tables');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTables();
  }, []);

  const handleAdd = () => {
    setEditingTable(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (record: SysSensitiveTable) => {
    setEditingTable(record);
    form.setFieldsValue(record);
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await sensitiveTableService.delete(id);
      message.success('Table deleted successfully');
      fetchTables();
    } catch (error) {
      message.error('Failed to delete table');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      
      if (editingTable && editingTable.id) {
        await sensitiveTableService.update(editingTable.id, values);
        message.success('Table updated successfully');
      } else {
        await sensitiveTableService.create(values);
        message.success('Table created successfully');
      }
      
      setModalVisible(false);
      fetchTables();
    } catch (error) {
      // Form validation error or API error
      console.error(error);
    } finally {
      setSubmitting(false);
    }
  };

  const getSensitivityColor = (level: number) => {
    switch (level) {
      case 1: return 'blue';
      case 2: return 'orange';
      case 3: return 'red';
      case 4: return 'purple';
      default: return 'default';
    }
  };

  const columns = [
    {
      title: '表名',
      dataIndex: 'tableName',
      key: 'tableName',
      render: (text: string) => (
        <div className="flex items-center gap-2">
          <Database size={16} className="text-gray-500" />
          <span className="font-medium">{text}</span>
        </div>
      ),
    },
    {
      title: '敏感等级',
      dataIndex: 'sensitivityLevel',
      key: 'sensitivityLevel',
      render: (level: number) => (
        <Tag color={getSensitivityColor(level)}>
          {level} 级
        </Tag>
      ),
    },
    {
      title: '风险系数',
      dataIndex: 'coefficient',
      key: 'coefficient',
      render: (coeff: number) => (
        <span className="font-mono bg-gray-100 px-2 py-1 rounded">x{coeff}</span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: any, record: SysSensitiveTable) => (
        <div className="flex gap-2">
          <Button 
            type="text" 
            icon={<Edit size={16} />} 
            onClick={() => handleEdit(record)}
            className="text-blue-600 hover:text-blue-800 hover:bg-blue-50"
          />
          <Popconfirm
            title="确认删除该表？"
            description="此操作无法撤销。"
            onConfirm={() => handleDelete(record.id!)}
            okText="是"
            cancelText="否"
          >
            <Button 
              type="text" 
              icon={<Trash2 size={16} />} 
              className="text-red-600 hover:text-red-800 hover:bg-red-50"
            />
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <div>
      <div className="mb-6 flex justify-between items-center">
        <div>
          <Title level={2} style={{ margin: 0 }}>敏感表管理</Title>
          <Text type="secondary">管理敏感数据库表及其风险等级</Text>
        </div>
        <Button type="primary" icon={<Plus size={16} />} onClick={handleAdd}>
          添加敏感表
        </Button>
      </div>

      <Card bordered={false} className="shadow-sm">
        <Table 
          columns={columns} 
          dataSource={tables} 
          rowKey="id" 
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Modal
        title={editingTable ? "编辑敏感表" : "添加敏感表"}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => setModalVisible(false)}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="tableName"
            label="表名"
            rules={[{ required: true, message: '请输入表名' }]}
          >
            <Input prefix={<Database size={16} className="text-gray-400" />} placeholder="例如：sys_user" />
          </Form.Item>

          <Form.Item
            name="sensitivityLevel"
            label="敏感等级"
            rules={[{ required: true, message: '请选择敏感等级' }]}
            initialValue={1}
          >
            <Select>
              <Option value={1}>1级 - 低 (内部)</Option>
              <Option value={2}>2级 - 中 (机密)</Option>
              <Option value={3}>3级 - 高 (秘密)</Option>
              <Option value={4}>4级 - 极高 (绝密)</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="coefficient"
            label="风险系数"
            rules={[{ required: true, message: '请输入风险系数' }]}
            initialValue={1.0}
            help="访问此表时风险分计算的乘数"
          >
            <InputNumber step={0.1} min={0.1} max={10.0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
        
        <div className="bg-yellow-50 p-3 rounded-md flex gap-2 items-start mt-4">
          <AlertTriangle className="text-yellow-600 shrink-0 mt-0.5" size={16} />
          <Text className="text-yellow-700 text-xs">
            修改敏感等级将立即影响实时风险评分。请确保风险系数符合您的安全策略。
          </Text>
        </div>
      </Modal>
    </div>
  );
};

export default SensitiveTables;

import React, { useState, useEffect } from 'react';
import { Table, Card, Tag, Button, Space, message, Typography, Tooltip } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { auditService } from '../services/api';
import { SysAuditLog } from '../types';
import dayjs from 'dayjs';

const { Title } = Typography;

const AuditLog: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<SysAuditLog[]>([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchData = async (page: number = 1, size: number = 10) => {
    setLoading(true);
    try {
      const res = await auditService.getLogs(page, size);
      setData(res.records);
      setPagination({
        current: res.current,
        pageSize: res.size,
        total: res.total,
      });
    } catch (error) {
      message.error('加载审计日志失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleFeedback = async (traceId: string, status: number) => {
    try {
      await auditService.submitFeedback(traceId, status);
      message.success('反馈提交成功');
      // Update local state to reflect change immediately
      setData(prev => prev.map(item => 
        item.traceId === traceId ? { ...item, feedbackStatus: status } : item
      ));
    } catch (error) {
      message.error('提交反馈失败');
    }
  };

  const columns = [
    {
      title: '时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (text: string) => dayjs(text).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: '用户',
      dataIndex: 'appUserId',
      key: 'appUserId',
      width: 120,
    },
    {
      title: 'SQL 模板',
      dataIndex: 'sqlTemplate',
      key: 'sqlTemplate',
      ellipsis: {
        showTitle: false,
      },
      render: (sql: string) => (
        <Tooltip placement="topLeft" title={sql}>
          <span className="font-mono text-xs">{sql}</span>
        </Tooltip>
      ),
    },
    {
      title: '风险分',
      dataIndex: 'riskScore',
      key: 'riskScore',
      width: 100,
      render: (score: number) => {
        let color = 'green';
        if (score >= 90) color = 'red';
        else if (score >= 60) color = 'orange';
        return <Tag color={color}>{score}</Tag>;
      },
    },
    {
      title: '动作',
      dataIndex: 'actionTaken',
      key: 'actionTaken',
      width: 100,
      render: (action: string) => (
        <Tag color={action === 'BLOCK' ? 'error' : 'success'}>
          {action}
        </Tag>
      ),
    },
    {
      title: '反馈状态',
      key: 'feedback',
      width: 200,
      render: (_: any, record: SysAuditLog) => {
        if (record.feedbackStatus === 1) {
          return <Tag icon={<CheckCircleOutlined />} color="success">已标记正常</Tag>;
        }
        if (record.feedbackStatus === 2) {
          return <Tag icon={<CloseCircleOutlined />} color="error">已标记异常</Tag>;
        }
        
        return (
          <Space size="small">
            <Button 
              size="small" 
              type="link" 
              className="text-green-600"
              onClick={() => handleFeedback(record.traceId, 1)}
            >
              正常
            </Button>
            <Button 
              size="small" 
              type="link" 
              danger
              onClick={() => handleFeedback(record.traceId, 2)}
            >
              异常
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <div className="p-6">
      <div className="mb-6 flex justify-between items-center">
        <div>
          <Title level={2} className="mb-1">审计日志</Title>
          <span className="text-gray-500">实时监控数据库访问行为与风险判定</span>
        </div>
      </div>

      <Card>
        <Table
          columns={columns}
          dataSource={data}
          rowKey="traceId"
          pagination={{
            ...pagination,
            onChange: (page, size) => fetchData(page, size),
          }}
          loading={loading}
          scroll={{ x: 1000 }}
        />
      </Card>
    </div>
  );
};

export default AuditLog;

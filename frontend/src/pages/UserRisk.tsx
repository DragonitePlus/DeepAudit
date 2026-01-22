import React, { useEffect, useState } from 'react';
import { Table, Card, Tag, Button, Input, message, Typography } from 'antd';
import { Search, User, ShieldAlert, ShieldCheck, Shield } from 'lucide-react';
import { userRiskService } from '../services/api';
import { SysUserRiskProfile } from '../types';

const { Title, Text } = Typography;

const UserRisk: React.FC = () => {
  const [data, setData] = useState<SysUserRiskProfile[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchUsers = async (page = 1, size = 10) => {
    setLoading(true);
    try {
      const result = await userRiskService.getProfiles(page, size);
      setData(result.records);
      setPagination({
        current: result.current,
        pageSize: result.size,
        total: result.total,
      });
    } catch (error) {
      message.error('Failed to load user risk profiles');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers(pagination.current, pagination.pageSize);
  }, []);

  const handleTableChange = (newPagination: any) => {
    fetchUsers(newPagination.current, newPagination.pageSize);
  };

  const getRiskColor = (level: string) => {
    switch (level) {
      case 'NORMAL': return 'green';
      case 'OBSERVATION': return 'orange';
      case 'BLOCKED': return 'red';
      default: return 'default';
    }
  };

  const getRiskIcon = (level: string) => {
    switch (level) {
      case 'NORMAL': return <ShieldCheck size={16} />;
      case 'OBSERVATION': return <Shield size={16} />;
      case 'BLOCKED': return <ShieldAlert size={16} />;
      default: return null;
    }
  };

  const columns = [
    {
      title: 'App User ID',
      dataIndex: 'appUserId',
      key: 'appUserId',
      render: (text: string) => (
        <div className="flex items-center gap-2">
          <User size={16} className="text-gray-500" />
          <span className="font-medium">{text}</span>
        </div>
      ),
    },
    {
      title: '当前风险分',
      dataIndex: 'currentScore',
      key: 'currentScore',
      render: (score: number, record: SysUserRiskProfile) => {
        let color = 'text-green-600';
        if (record.riskLevel === 'OBSERVATION') color = 'text-orange-600';
        if (record.riskLevel === 'BLOCKED') color = 'text-red-600';
        
        return (
          <span className={`font-bold text-lg ${color}`}>
            {score}
          </span>
        );
      },
    },
    {
      title: '风险等级',
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      render: (level: string) => (
        <Tag color={getRiskColor(level)} className="flex items-center gap-1 w-fit px-2 py-1">
          {getRiskIcon(level)}
          {level}
        </Tag>
      ),
    },
    {
      title: '最后更新时间',
      dataIndex: 'lastUpdateTime',
      key: 'lastUpdateTime',
      render: (text: string) => text ? new Date(text).toLocaleString() : '-',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
  ];

  return (
    <div>
      <div className="mb-6 flex justify-between items-center">
        <div>
          <Title level={2} style={{ margin: 0 }}>用户风险画像</Title>
          <Text type="secondary">实时监控所有应用用户的风险分数和状态</Text>
        </div>
        <div className="flex gap-2">
          <Input 
            prefix={<Search size={16} className="text-gray-400" />} 
            placeholder="搜索用户 ID" 
            style={{ width: 250 }} 
          />
          <Button type="primary" onClick={() => fetchUsers(1, pagination.pageSize)}>搜索</Button>
        </div>
      </div>

      <Card bordered={false} className="shadow-sm">
        <Table 
          columns={columns} 
          dataSource={data} 
          rowKey="appUserId" 
          loading={loading}
          pagination={pagination}
          onChange={handleTableChange}
        />
      </Card>
    </div>
  );
};

export default UserRisk;

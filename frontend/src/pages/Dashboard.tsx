import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Spin } from 'antd';
import { ShieldAlert, Users, Activity, FileWarning } from 'lucide-react';
import ReactECharts from 'echarts-for-react';
import { userRiskService, sensitiveTableService, auditService } from '../services/api';

const Dashboard: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalUsers: 0,
    highRiskUsers: 0,
    blockedUsers: 0,
    sensitiveTables: 0,
  });

  const [chartData, setChartData] = useState<any[]>([]);
  const [trendData, setTrendData] = useState<{ x: string; y: number }[]>([]);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        // Fetch stats, users, tables, and trend in parallel
        const [statsResult, usersResult, tables, trend] = await Promise.all([
          userRiskService.getStats(),
          userRiskService.getProfiles(1, 1000), // Get samples for chart distribution
          sensitiveTableService.getAll(),
          auditService.getTrend()
        ]);

        const users = usersResult.records;
        const highRisk = users.filter(u => u.riskLevel === 'OBSERVATION').length;
        const blocked = users.filter(u => u.riskLevel === 'BLOCKED').length;

        setStats({
          totalUsers: statsResult.totalUsers, // Use real total from DB
          highRiskUsers: highRisk,
          blockedUsers: blocked,
          sensitiveTables: tables.length,
        });

        // Mock chart data based on risk distribution
        // We calculate 'Normal' as Total - (Observation + Blocked) to reflect real status
        // Or strictly use sampled data if total is too large
        const monitoredNormal = users.filter(u => u.riskLevel === 'NORMAL').length;
        
        setChartData([
          { value: monitoredNormal, name: '正常' },
          { value: highRisk, name: '观察期' },
          { value: blocked, name: '已阻断' },
        ]);

        setTrendData(trend.map(t => ({ x: t.timeSlot, y: t.totalScore })));

      } catch (error) {
        console.error('Failed to fetch dashboard data', error);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
    // Poll every 30 seconds
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, []);

  const pieOption = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      top: '5%',
      left: 'center'
    },
    series: [
      {
        name: '风险等级分布',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 40,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: chartData,
        color: ['#10B981', '#F59E0B', '#EF4444'] 
      }
    ]
  };

  const lineOption = {
    title: {
      text: '24小时风险积分趋势'
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: trendData.map(d => d.x)
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '总风险分',
        data: trendData.map(d => d.y),
        type: 'line',
        smooth: true,
        areaStyle: {},
        color: '#3B82F6'
      }
    ]
  };

  if (loading && stats.totalUsers === 0) {
    return (
      <div className="flex justify-center items-center h-full">
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div>
      <h2 className="text-2xl font-bold mb-6 text-gray-800">系统风险概览</h2>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" className="shadow-sm hover:shadow-md transition-shadow">
            <Statistic
              title="总用户数"
              value={stats.totalUsers}
              prefix={<Users size={20} className="text-blue-500 mr-2" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" className="shadow-sm hover:shadow-md transition-shadow">
            <Statistic
              title="高风险 (观察期)"
              value={stats.highRiskUsers}
              prefix={<Activity size={20} className="text-orange-500 mr-2" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" className="shadow-sm hover:shadow-md transition-shadow">
            <Statistic
              title="已阻断用户"
              value={stats.blockedUsers}
              prefix={<ShieldAlert size={20} className="text-red-500 mr-2" />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" className="shadow-sm hover:shadow-md transition-shadow">
            <Statistic
              title="敏感表数量"
              value={stats.sensitiveTables}
              prefix={<FileWarning size={20} className="text-purple-500 mr-2" />}
            />
          </Card>
        </Col>
      </Row>
      
      <div className="mt-8 grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card title="风险分布" variant="borderless" className="shadow-sm">
          <ReactECharts option={pieOption} style={{ height: '300px' }} />
        </Card>
        <Card title="风险趋势分析" variant="borderless" className="shadow-sm">
          <ReactECharts option={lineOption} style={{ height: '300px' }} />
        </Card>
      </div>
    </div>
  );
};

export default Dashboard;

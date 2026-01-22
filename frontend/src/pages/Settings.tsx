import React from 'react';
import { Card, Typography, Row, Col, ColorPicker, Divider, Descriptions, Button } from 'antd';
import { Settings as SettingsIcon, Palette, Info } from 'lucide-react';
import { useStore } from '../store/useStore';

const { Title, Text } = Typography;

const Settings: React.FC = () => {
  const { primaryColor, setPrimaryColor } = useStore();

  const handleColorChange = (value: any) => {
    // Ant Design ColorPicker returns an object, we need the hex string
    const hex = value.toHexString();
    setPrimaryColor(hex);
  };

  return (
    <div>
      <div className="mb-6">
        <Title level={2} style={{ margin: 0 }}>系统设置</Title>
        <Text type="secondary">管理系统外观和全局参数</Text>
      </div>

      <Row gutter={[24, 24]}>
        <Col span={24} lg={12}>
          <Card 
            title={
              <div className="flex items-center gap-2">
                <Palette size={20} className="text-blue-500" />
                <span>外观设置</span>
              </div>
            }
            bordered={false} 
            className="shadow-sm h-full"
          >
            <div className="flex items-center justify-between mb-4">
              <div>
                <h4 className="font-medium text-gray-800 m-0">系统主题色</h4>
                <p className="text-gray-500 text-sm m-0">自定义系统的主色调，应用于按钮、链接和导航栏</p>
              </div>
              <ColorPicker 
                value={primaryColor} 
                onChange={handleColorChange}
                showText
              />
            </div>
            <Divider />
            <div className="flex items-center justify-between">
              <div>
                <h4 className="font-medium text-gray-800 m-0">界面模式</h4>
                <p className="text-gray-500 text-sm m-0">切换浅色/深色模式 (暂未实装)</p>
              </div>
              <Button disabled>切换模式</Button>
            </div>
          </Card>
        </Col>

        <Col span={24} lg={12}>
          <Card 
            title={
              <div className="flex items-center gap-2">
                <Info size={20} className="text-blue-500" />
                <span>关于系统</span>
              </div>
            }
            bordered={false} 
            className="shadow-sm h-full"
          >
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="系统名称">DeepAudit 数据库审计系统</Descriptions.Item>
              <Descriptions.Item label="当前版本">v1.0.0 (Beta)</Descriptions.Item>
              <Descriptions.Item label="构建时间">2026-01-22</Descriptions.Item>
              <Descriptions.Item label="技术栈">React 18 + Vite + Spring Boot 3 + Redis</Descriptions.Item>
              <Descriptions.Item label="开发者">DeepAudit Team</Descriptions.Item>
            </Descriptions>
            
            <div className="mt-6 text-center text-gray-400 text-sm">
              &copy; 2026 DeepAudit. All rights reserved.
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Settings;

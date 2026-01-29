import React, { useEffect, useState } from 'react';
import { Card, Form, InputNumber, Button, Slider, Tooltip, message, Spin, Row, Col, Typography, Popconfirm, Input } from 'antd';
import { Info, Save, RotateCcw, Cpu } from 'lucide-react';
import { riskConfigService } from '../services/api';
import { RiskProperties } from '../types';

const { Title, Text } = Typography;

const AlgorithmConfig: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const fetchConfig = async () => {
    setLoading(true);
    try {
      const config = await riskConfigService.getConfig();
      form.setFieldsValue(config);
    } catch (error) {
      message.error('加载配置失败');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfig();
  }, []);

  const onFinish = async (values: RiskProperties) => {
    setSaving(true);
    try {
      await riskConfigService.updateConfig(values);
      message.success('配置更新成功');
    } catch (error) {
      message.error('更新配置失败');
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    form.resetFields();
    fetchConfig();
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-full">
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6 flex justify-between items-center">
        <div>
          <Title level={2} style={{ margin: 0 }}>风险算法配置</Title>
          <Text type="secondary">配置 RAdAC 风险评估引擎的核心参数</Text>
        </div>
        <Button icon={<RotateCcw size={16} />} onClick={handleReset}>
          重置
        </Button>
      </div>

      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{
          decayRate: 0.5,
          observationThreshold: 40,
          blockThreshold: 100,
          windowTtl: 300,
          mlWeight: 0.3
        }}
      >
        <Row gutter={24}>
          <Col span={24} lg={12}>
            <Card title="阈值设置" bordered={false} className="shadow-sm mb-6 h-full">
              <Form.Item
                name="observationThreshold"
                label={
                  <span className="flex items-center gap-1">
                    观测阈值
                    <Tooltip title="当风险分高于此值时触发观测模式">
                      <Info size={14} className="text-gray-400" />
                    </Tooltip>
                  </span>
                }
                rules={[
                  { required: true, message: '请输入观测阈值' },
                  { type: 'number', min: 1, max: 100, message: '必须在 1-100 之间' }
                ]}
              >
                <InputNumber min={1} max={100} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item
                name="blockThreshold"
                label={
                  <span className="flex items-center gap-1">
                    阻断阈值
                    <Tooltip title="当风险分高于此值时触发阻断">
                      <Info size={14} className="text-gray-400" />
                    </Tooltip>
                  </span>
                }
                rules={[
                  { required: true, message: '请输入阻断阈值' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || value > getFieldValue('observationThreshold')) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error('阻断阈值必须大于观测阈值'));
                    },
                  }),
                ]}
              >
                <InputNumber min={1} max={200} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item
                name="windowTtl"
                label={
                  <span className="flex items-center gap-1">
                    时间窗口 TTL (秒)
                    <Tooltip title="观测窗口的存活时间">
                      <Info size={14} className="text-gray-400" />
                    </Tooltip>
                  </span>
                }
                rules={[{ required: true, message: '请输入时间窗口 TTL' }]}
              >
                <InputNumber min={1} max={3600} style={{ width: '100%' }} />
              </Form.Item>
            </Card>
          </Col>

          <Col span={24} lg={12}>
            <Card title="算法参数" bordered={false} className="shadow-sm mb-6 h-full">
              <Form.Item
                name="decayRate"
                label={
                  <span className="flex items-center gap-1">
                    衰减率 (分/秒)
                    <Tooltip title="风险分随时间自动衰减的速率">
                      <Info size={14} className="text-gray-400" />
                    </Tooltip>
                  </span>
                }
                rules={[{ required: true, message: '请输入衰减率' }]}
              >
                <InputNumber step={0.1} min={0} max={10} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item
                name="mlWeight"
                label={
                  <span className="flex items-center gap-1">
                    机器学习权重
                    <Tooltip title="机器学习评分在最终风险评估中的权重 (0.0 - 1.0)">
                      <Info size={14} className="text-gray-400" />
                    </Tooltip>
                  </span>
                }
              >
                <Row gutter={16}>
                  <Col span={16}>
                    <Slider min={0} max={1} step={0.1} onChange={(v) => form.setFieldsValue({ mlWeight: v })} value={form.getFieldValue('mlWeight')} />
                  </Col>
                  <Col span={8}>
                    <InputNumber min={0} max={1} step={0.1} style={{ width: '100%' }} />
                  </Col>
                </Row>
              </Form.Item>
              
              <Form.Item
                name="modelPath"
                label={
                  <span className="flex items-center gap-1">
                    模型路径 (ONNX)
                    <Tooltip title="服务器上 ONNX 模型文件的绝对路径">
                      <Info size={14} className="text-gray-400" />
                    </Tooltip>
                  </span>
                }
                rules={[{ required: true, message: '请输入模型路径' }]}
              >
                <Input placeholder="例如: D:/Code/DeepAudit/models/deep_audit_iso_forest.onnx" />
              </Form.Item>

              <div className="bg-blue-50 p-4 rounded-lg mt-6 flex items-start gap-3">
                <Cpu className="text-blue-500 mt-1" size={24} />
                <div>
                  <h4 className="font-medium text-blue-900 m-0">算法逻辑</h4>
                  <p className="text-blue-700 text-sm mt-1">
                    当前分数 = 上一时刻分数 - 衰减值 + 新增分数 * (1 - ML权重) + ML评分 * ML权重
                  </p>
                </div>
              </div>
            </Card>
          </Col>
        </Row>

        <div className="flex justify-end">
          <Popconfirm
            title="确认保存配置？"
            description="修改风险算法参数可能会影响系统的安全策略，请确认无误。"
            onConfirm={form.submit}
            okText="确认保存"
            cancelText="取消"
          >
            <Button 
              type="primary" 
              icon={<Save size={16} />} 
              loading={saving}
              size="large"
            >
              保存配置
            </Button>
          </Popconfirm>
        </div>
      </Form>
    </div>
  );
};

export default AlgorithmConfig;

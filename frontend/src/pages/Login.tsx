import React, { useState } from 'react';
import { Form, Input, Button, Card, message, Typography } from 'antd';
import { User, Lock } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useStore } from '../store/useStore';

const { Title, Text } = Typography;

const Login: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useStore();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      // TODO: Call actual API
      // Mock login for now
      console.log('Login values:', values);
      setTimeout(() => {
        login({ id: '1', username: values.username, role: 'admin' }, 'mock-token');
        message.success('登录成功');
        navigate('/dashboard');
        setLoading(false);
      }, 1000);
    } catch (error) {
      message.error('登录失败');
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-100">
      <Card className="w-full max-w-md shadow-xl rounded-2xl" variant="borderless">
        <div className="text-center mb-8">
          <div className="flex justify-center mb-4">
             <div className="w-16 h-16 bg-blue-900 rounded-lg flex items-center justify-center text-white font-bold text-2xl">DA</div>
          </div>
          <Title level={2} style={{ color: '#1E3A8A', margin: 0 }}>DeepAudit</Title>
          <Text type="secondary" className="mt-2 block">数据库审计管理系统</Text>
        </div>
        <Form
          name="login"
          initialValues={{ remember: true }}
          onFinish={onFinish}
          size="large"
          layout="vertical"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<User size={18} className="text-gray-400" />} placeholder="用户名" />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<Lock size={18} className="text-gray-400" />} placeholder="密码" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading} className="bg-blue-900 hover:bg-blue-800 h-12 text-lg font-medium">
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default Login;

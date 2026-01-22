import React, { useState } from 'react';
import { Layout, Menu, Button, Avatar, Dropdown, theme } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  LayoutDashboard,
  Users,
  Cpu,
  Database,
  Settings,
  Menu as MenuIcon,
  LogOut,
  User,
  Bell
} from 'lucide-react';
import { useStore } from '../store/useStore';

const { Header, Sider, Content } = Layout;

const MainLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout, user } = useStore();
  const [collapsed, setCollapsed] = useState(false);
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const menuItems = [
    {
      key: '/dashboard',
      icon: <LayoutDashboard size={20} />,
      label: '风险概览',
    },
    {
      key: '/users',
      icon: <Users size={20} />,
      label: '用户风险查询',
    },
    {
      key: '/algorithms',
      icon: <Cpu size={20} />,
      label: '风险算法配置',
    },
    {
      key: '/sensitive-tables',
      icon: <Database size={20} />,
      label: '敏感表管理',
    },
    {
      key: '/settings',
      icon: <Settings size={20} />,
      label: '系统设置',
    },
  ];

  const userMenu = {
    items: [
      {
        key: 'profile',
        label: '个人中心',
        icon: <User size={16} />,
      },
      {
        key: 'logout',
        label: '退出登录',
        icon: <LogOut size={16} />,
        onClick: handleLogout,
      },
    ],
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider trigger={null} collapsible collapsed={collapsed} theme="dark" width={240}>
        <div className="flex items-center justify-center h-16 bg-gray-900 text-white font-bold text-xl overflow-hidden">
          {collapsed ? 'DA' : 'DeepAudit'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          onClick={({ key }) => navigate(key)}
          items={menuItems}
        />
      </Sider>
      <Layout>
        <Header style={{ padding: 0, background: colorBgContainer }} className="flex items-center justify-between px-4 shadow-sm">
          <Button
            type="text"
            icon={<MenuIcon size={20} />}
            onClick={() => setCollapsed(!collapsed)}
            style={{
              fontSize: '16px',
              width: 64,
              height: 64,
            }}
          />
          <div className="flex items-center gap-4 pr-4">
            <Button type="text" icon={<Bell size={20} />} className="flex items-center justify-center" />
            <Dropdown menu={userMenu} placement="bottomRight">
              <div className="flex items-center gap-2 cursor-pointer hover:bg-gray-100 p-2 rounded-lg transition-colors">
                <Avatar style={{ backgroundColor: '#1E3A8A' }} icon={<User size={20} />} />
                <span className="font-medium text-gray-700">{user?.username || '管理员'}</span>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content
          style={{
            margin: '24px 16px',
            padding: 24,
            minHeight: 280,
            background: colorBgContainer,
            borderRadius: borderRadiusLG,
            overflowY: 'auto',
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;

# 黑马点评项目

## 1. 基于Redis的短信登录

### 1.1 基于Session的登录流程

#### 发送短信验证码

- 接口：`/user/code`

- 流程：

<img src="./assets/Snipaste_2024-01-10_19-11-32.jpg" style="zoom: 67%;" />

#### 短信验证码登录、注册

- 接口：`/user/login`

- 流程：

<img src="./assets/Snipaste_2024-01-10_19-13-48.jpg" style="zoom:67%;" />

#### 校验登录状态（使用拦截器进行校验）

- 流程：

<img src="./assets/Snipaste_2024-01-10_19-14-53.jpg" style="zoom:67%;" />

#### 基于Session登录的问题

- 多台Tomcat并不共享session存储空间，当请求切换到不同tomcat服务时导致数据丢失问题

### 1.2 基于Redis实现共享登录

#### 发送短信验证码

- 流程

  <img src="./assets/Snipaste_2024-01-10_19-19-18.jpg" style="zoom:67%;" />

#### 短信验证码登录、注册

- 流程

  ![](./assets/Snipaste_2024-01-10_19-20-56.jpg)

### 1.3 技术细节

#### 依赖注入问题

![](./assets/Snipaste_2024-01-10_19-23-49.jpg)

`RefreshTokenInterceptor`的实例是通过`new`的方式配置在拦截器配置类中，所以不能使用`@Autowired`或`@Resourcs`进行依赖注入

#### 拦截器设置和配置

![](./assets/Snipaste_2024-01-10_19-25-59.jpg)

- 配置两个拦截器
  - 第一个拦截器拦截**所有路径**，主要功能为：刷新token的有效期，并将当前用户保存到`ThreadLocal`中；均放行；
  - 第二个拦截器拦截**需要登录校验的路径**，主要功能为：进行登录校验，即判断当前`ThreadLocal`中是否有用户信息；
  - 两个拦截器的顺序可以通过`order()`进行配置；

#### Redis中key和value的存储形式

- 验证码的存储存为`String`类型；

- 用户信息，即`UserDTO`对象，存为`Hash`类型，具体代码如下：

  ![](./assets/Snipaste_2024-01-10_19-33-35.jpg)

  - `setFileValueEditor()`方法将原始字段值转换成它的 `String` 表示；
  - `putAll()`方法接收两个参数，分别为`key`和`map`形式的`value`；
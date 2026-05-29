/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
//    private String replaceSqlConstant(String sql) {
//        sql = sql.trim();
//        sql = sql.replaceAll("\\[LOGIN_USER_ID\\]",     CurrentUser.getUserId().toString());     // 当前登录用户ID
//        sql = sql.replaceAll("\\[LOGIN_ORGANIZE_ID\\]", CurrentUser.getOrganizeId().toString()); // 当前登录用户的机构ID
//        StringBuilder roleIds = new StringBuilder();
//        List<String> roles = CurrentUser.getRoles4String();
//        if (roles.size() == 1) { // 能进入到该方法，roles一定是有值的，所以不需要再判断null
//            roleIds.append(" = ").append(roles.get(0));
//        } else if (roles.size() > 1) {
//            roleIds.append(" IN (").append(String.join(", ", roles)).append(") ");
//        }
//        sql = sql.replaceAll("(=|in|IN){1}\\s*\\[LOGIN_USER_ROLE\\]", roleIds.toString());// 当前登录用户的角色ID
//        return sql;
//    }
//
//    @Override
//    public Object plugin(Object target) {
//        return Plugin.wrap(target, this);
//    }
//
//    @Override
//    public void setProperties(Properties properties) {
//        // do nothing
//    }
//}

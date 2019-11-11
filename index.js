import { NativeModules } from 'react-native';

const { WeiboAPI } = NativeModules;

const defaultScope = "all";
const defaultRedirectURI = "https://api.weibo.com/oauth2/default.html";

export const login = WeiboAPI.login;
export const share = WeiboAPI.share;
export const register = ({ appId, redirectURI = defaultRedirectURI, scope = defaultScope }) => WeiboAPI.register({
  appId,
  redirectURI,
  scope
});

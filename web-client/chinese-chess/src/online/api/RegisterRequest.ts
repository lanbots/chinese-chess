import User from "../../user/User";
import { APIRequest, HttpMethod } from "./api_request";

export default class RegisterRequest extends APIRequest {
    constructor(user: User) {
        super();
        this.method = HttpMethod.POST;
        this.path = 'users';

        this.addParam('nickname', user.nickname);
        this.addParam('password', user.password);
    }
}
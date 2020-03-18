import * as tslib_1 from "tslib";
import { NativeTransport } from "../../src/js/transports/native";
jest.mock("../../src/js/wrapper", () => ({
    NATIVE: {
        sendEvent: jest.fn(() => Promise.resolve({ status: 200 }))
    }
}));
describe("NativeTransport", () => {
    test("call native sendEvent", () => tslib_1.__awaiter(this, void 0, void 0, function* () {
        const transport = new NativeTransport();
        yield expect(transport.sendEvent({})).resolves.toEqual({ status: 200 });
    }));
});
//# sourceMappingURL=native.test.js.map
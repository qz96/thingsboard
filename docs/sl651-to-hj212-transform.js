// ThingsBoard 规则链 - Transform Script 节点
// 功能：把 SL651 要素编码键翻译为 HJ212 污染因子编码
//
// 背景：
//  - SL651 传输（TB 原生 transport）落库键 = SL651 完整 2 字节数据标识符
//    （引导符<<8 | 属性字，如 4612 / 4711 / ...），而非中文要素名。
//  - 本脚本把这些编码键映射为 HJ212-2025 污染因子编码，供下游 Save Timeseries 落库。
//
// 使用：
//  1. 在规则链中创建 Transform Script 节点，粘贴本脚本。
//  2. 按你旧网关 sl-gateway 的 seed-mapping.sql / 要素映射页，校准 FACTOR_MAP 的 code。
//  3. 链编排：入口 → Message Type Switch → Transform Script → Save Timeseries。
//
// 说明：下方 code 为示例（含 2025 举例），务必以实际厂商/旧网关映射为准。

var FACTOR_MAP = {
  // 以上实测帧的要素标识符为例，code 为 HJ212 污染因子编码示例
  '4612': { code: 'W21003', name: 'pH' },
  '4711': { code: 'W21001', name: '溶解氧' },
  '4818': { code: 'W21021', name: '氨氮' },
  '4910': { code: 'W21011', name: '浊度' },
  '4A11': { code: 'W21005', name: '电导率' },
  '4C1A': { code: 'W21016', name: '总磷' },
  '4D1B': { code: 'W21017', name: '总氮' },
  '4E1A': { code: 'W21019', name: '高锰酸盐指数' },
  '4520': { code: 'D001', name: '遥测站状态及报警信息' },
  '381A': { code: 'D002', name: '电源电压' }
};

var result = {};
var keys = Object.keys(msg);
for (var i = 0; i < keys.length; i++) {
  var f = FACTOR_MAP[keys[i]];
  if (f) {
    result[f.code] = msg[keys[i]];
  }
}

// 如需同时保留原始编码键，可取消下一行注释：
// result.raw = msg;

return { msg: result, metadata: metadata, msgType: msgType };
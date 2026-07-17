// pages/bill/bill.js
Page({
  data: {
    billList: []
  },

  // 每次页面显示（比如从记账页切过来）都会触发
  onShow: function () {
    this.fetchBillList();
  },

  fetchBillList: function () {
    // 从存储里拿出账单数组
    const list = wx.getStorageSync('billList') || [];
    
    // 给数据做个简单的排序（按时间戳降序，最新记录在前）
    // 就像 C++ 里的 sort，这里用 JS 数组的 sort 方法
    list.sort((a, b) => b.timestamp - a.timestamp);

    this.setData({
      billList: list
    });
  }
})

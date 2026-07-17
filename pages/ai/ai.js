// pages/ai/ai.js
Page({
  data: {
    money: '',
    type: '支出',
    categories: ['三餐', '夜宵', '出行', '购物', '娱乐', '其他'],
    categoryMarks: ['餐', '夜', '行', '购', '玩', '+'],
    categoryIndex: 0,
    otherCategory: '',
    todayExpense: '0.00',
    monthExpense: '0.00',
    balance: '0.00',
    billCount: 0
  },

  onShow() {
    this.loadOverview();
  },

  inputMoney(e) { this.setData({ money: e.detail.value }); },
  typeChange(e) { this.setData({ type: e.detail.value }); },
  categoryChange(e) { this.setData({ categoryIndex: e.detail.value, otherCategory: '' }); },
  inputOtherCategory(e) { this.setData({ otherCategory: e.detail.value }); },

  selectType(e) {
    this.setData({ type: e.currentTarget.dataset.type });
  },

  selectCategory(e) {
    this.setData({
      categoryIndex: Number(e.currentTarget.dataset.index),
      otherCategory: ''
    });
  },

  pad(num) {
    num = Number(num);
    return num < 10 ? `0${num}` : String(num);
  },

  formatDateKey(date) {
    const year = date.getFullYear();
    const month = this.pad(date.getMonth() + 1);
    const day = this.pad(date.getDate());
    return `${year}-${month}-${day}`;
  },

  getRecordDateKey(record) {
    if (record.dateKey) {
      return record.dateKey;
    }
    const time = Number(record.timestamp || record.id || Date.now());
    return this.formatDateKey(new Date(time));
  },

  toMoney(num) {
    return Number(num || 0).toFixed(2);
  },

  loadOverview() {
    const billList = wx.getStorageSync('billList') || [];
    const now = new Date();
    const todayKey = this.formatDateKey(now);
    const monthKey = `${now.getFullYear()}-${this.pad(now.getMonth() + 1)}`;
    let income = 0;
    let expense = 0;
    let todayExpense = 0;
    let monthExpense = 0;

    for (let i = 0; i < billList.length; i++) {
      const item = billList[i];
      const amount = Number(item.money) || 0;
      const dateKey = this.getRecordDateKey(item);

      if (item.type === '收入') {
        income += amount;
      } else {
        expense += amount;
        if (dateKey === todayKey) {
          todayExpense += amount;
        }
        if (dateKey.indexOf(monthKey) === 0) {
          monthExpense += amount;
        }
      }
    }

    this.setData({
      todayExpense: this.toMoney(todayExpense),
      monthExpense: this.toMoney(monthExpense),
      balance: this.toMoney(income - expense),
      billCount: billList.length
    });
  },

  saveRecord() {
    const { money, type, categories, categoryIndex, otherCategory } = this.data;

    if (!money || isNaN(money)) {
      wx.showToast({ title: '请输入正确金额', icon: 'none' });
      return;
    }

    let finalCategory = categories[categoryIndex];
    if (finalCategory === '其他') {
      finalCategory = otherCategory || '其他';
    }

    const now = new Date();

    // 组装新记录
    const newRecord = {
      id: Date.now(),
      money: parseFloat(money).toFixed(2),
      type: type,
      category: finalCategory,
      date: now.toLocaleString(),
      dateKey: this.formatDateKey(now),
      timestamp: now.getTime()
    };

    let billList = wx.getStorageSync('billList') || [];
    billList.unshift(newRecord);
    wx.setStorageSync('billList', billList);

    wx.showToast({ title: '记账成功', icon: 'success' });

    this.setData({
      money: '',
      otherCategory: '',
      categoryIndex: 0
    });
    this.loadOverview();
  }
})

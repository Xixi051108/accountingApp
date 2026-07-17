Page({
  data: {
    totalIncome: '0.00',
    totalExpense: '0.00',
    balance: '0.00',
    monthIncome: '0.00',
    monthExpense: '0.00',
    todayExpense: '0.00',
    monthTitle: '',
    balanceClass: 'positive',
    categoryStats: [],
    latestBills: [],
    hasData: false
  },

  onShow() {
    this.buildStats();
  },

  pad(num) {
    num = Number(num);
    return num < 10 ? `0${num}` : String(num);
  },

  formatDateKey(date) {
    return `${date.getFullYear()}-${this.pad(date.getMonth() + 1)}-${this.pad(date.getDate())}`;
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

  buildStats() {
    const bills = wx.getStorageSync('billList') || [];
    const now = new Date();
    const todayKey = this.formatDateKey(now);
    const monthKey = `${now.getFullYear()}-${this.pad(now.getMonth() + 1)}`;
    const monthTitle = `${now.getFullYear()}年${now.getMonth() + 1}月`;

    let totalIncome = 0;
    let totalExpense = 0;
    let monthIncome = 0;
    let monthExpense = 0;
    let todayExpense = 0;
    const categoryMap = {};

    for (let i = 0; i < bills.length; i++) {
      const item = bills[i];
      const amount = Number(item.money) || 0;
      const dateKey = this.getRecordDateKey(item);
      const isIncome = item.type === '收入';

      if (isIncome) {
        totalIncome += amount;
      } else {
        totalExpense += amount;
        categoryMap[item.category] = (categoryMap[item.category] || 0) + amount;
      }

      if (dateKey.indexOf(monthKey) === 0) {
        if (isIncome) {
          monthIncome += amount;
        } else {
          monthExpense += amount;
        }
      }

      if (dateKey === todayKey && !isIncome) {
        todayExpense += amount;
      }
    }

    let maxCategoryAmount = 0;
    const categoryNames = Object.keys(categoryMap);
    for (let i = 0; i < categoryNames.length; i++) {
      const amount = categoryMap[categoryNames[i]];
      if (amount > maxCategoryAmount) {
        maxCategoryAmount = amount;
      }
    }
    const categoryStats = categoryNames
      .map(name => {
        const amount = categoryMap[name];
        const percent = maxCategoryAmount ? Math.max(8, Math.round((amount / maxCategoryAmount) * 100)) : 0;
        return {
          name,
          amount: this.toMoney(amount),
          percent
        };
      })
      .sort((a, b) => Number(b.amount) - Number(a.amount))
      .slice(0, 6);

    const latestBills = bills
      .slice()
      .sort((a, b) => (b.timestamp || b.id) - (a.timestamp || a.id))
      .slice(0, 5)
      .map(item => ({
        id: item.id,
        category: item.category,
        type: item.type,
        money: item.money,
        date: item.date || this.getRecordDateKey(item)
      }));

    this.setData({
      totalIncome: this.toMoney(totalIncome),
      totalExpense: this.toMoney(totalExpense),
      balance: this.toMoney(totalIncome - totalExpense),
      balanceClass: totalIncome - totalExpense >= 0 ? 'positive' : 'negative',
      monthIncome: this.toMoney(monthIncome),
      monthExpense: this.toMoney(monthExpense),
      todayExpense: this.toMoney(todayExpense),
      monthTitle,
      categoryStats,
      latestBills,
      hasData: bills.length > 0
    });
  }
});

package com.techelevator.tenmo.dao;

import com.techelevator.tenmo.model.Account;
import com.techelevator.tenmo.model.Transfer;
//import com.techelevator.tenmo.model.TransferForMenu;
import com.techelevator.tenmo.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class JdbcTransferDao implements TransferDao {

    private final JdbcTemplate jdbcTemplate;
    private final AccountDao accountDao;
    private Account account;
    private final UserDao userDao;

    public JdbcTransferDao(JdbcTemplate jdbcTemplate, AccountDao accountDao, UserDao userDao) {  //Spring needs to run the sql request
        this.jdbcTemplate = jdbcTemplate;
        this.accountDao = accountDao;
        this.userDao = userDao;
    }


    @Override
    public String getTransferType(int transferId) {
        //data base query
        String sql = "SELECT transfer_type_desc \n" +
                "FROM transfer_type \n" +
                "JOIN transfer ON transfer_type.transfer_type_id = transfer.transfer_type_id \n" +
                "WHERE transfer_id = ?";

        //return result from the sql query
        return jdbcTemplate.queryForObject(sql, String.class, transferId);
    }

    @Override
    public void addToReceiverBalance(int userId, BigDecimal amount) {
        //data base query
        String sql = "UPDATE account SET balance = balance + ? WHERE user_id = ?";
        //return result from the sql query
        jdbcTemplate.update(sql, amount, userId);
    }

    @Override
    public void subtractFromSenderBalance(int userId, BigDecimal amount) {
        //data base query
        String sql = "UPDATE account SET balance = balance - ? WHERE user_id = ?";
        //return result from the sql query
        jdbcTemplate.update(sql, amount, userId);
    }

    @Override
    public boolean createTransfer(Transfer transfer) throws Exception {
        System.out.println(transfer.toString());
        //creates transfer record to sender
        String sql = "INSERT INTO transfer(transfer_type_id, transfer_status_id, account_from, account_to, amount)\n" +
                "VALUES (?, ?, (SELECT account_id FROM account WHERE user_id = ?), (SELECT account_id FROM account WHERE user_id = ?), ?) RETURNING transfer_id";
        BigDecimal amount = transfer.getAmount();
        boolean success = false;

        //check if the transfer is less than zero
        if (amount.compareTo(accountDao.getBalance(transfer.getSenderId())) <= 0 && amount.compareTo(BigDecimal.ZERO) > 0) {
            int id = jdbcTemplate.queryForObject(sql, int.class, 2, 2, transfer.getSenderId(), transfer.getReceiverId(), transfer.getAmount());
            sql = "SELECT * FROM transfer WHERE transfer_id = ?";
            SqlRowSet results = jdbcTemplate.queryForRowSet(sql, id);
            success = true;
            if (results.next()) {
                mapRowToTransfer(results);
            }
        } else
            throw new Exception("Transfer not logged.");
        return success;
    }



    @Override
    public boolean createTransferToSender(Transfer transfer) throws Exception {
        //creates transfer history to reciever
        System.out.println(transfer.toString());
        String sql = "INSERT INTO transfer(transfer_type_id, transfer_status_id, account_from, account_to, amount)\n" +
                "VALUES (?, ?, (SELECT account_id FROM account WHERE user_id = ?), (SELECT account_id FROM account WHERE user_id = ?), ?) RETURNING transfer_id";
        BigDecimal amount = transfer.getAmount();
        boolean success = false;

        if (amount.compareTo(accountDao.getBalance(transfer.getSenderId())) <= 0 && amount.compareTo(BigDecimal.ZERO) > 0) {
            int id = jdbcTemplate.queryForObject(sql, int.class, 2, 2, transfer.getReceiverId(), transfer.getSenderId(), transfer.getAmount());
            sql = "SELECT * FROM transfer WHERE transfer_id = ?";
            SqlRowSet results = jdbcTemplate.queryForRowSet(sql, id);
            success = true;
            if (results.next()) {
                mapRowToTransfer(results);
            }
        } else
            throw new Exception("Transfer not logged.");
        return success;
    }



    private Transfer mapRowToTransfer(SqlRowSet results) throws Exception {
        Transfer transfer = new Transfer();
        transfer.setTransferId(results.getInt("transfer_id"));
        transfer.setTransferTypeId(results.getInt("transfer_type_id"));
        transfer.setAccountFrom(results.getInt("account_from"));
        transfer.setAccountTo(results.getInt("account_to"));
        transfer.setAmount(BigDecimal.valueOf(Double.valueOf(results.getString("amount"))));
        transfer.setTransferStatus(results.getInt("transfer_status_id"));
        return transfer;
    }

    @Override
    public boolean checkBalanceBeforeTransfer(BigDecimal balance, BigDecimal amount) {
        return false;
    }

    @Override
    public List<Transfer> getTransfersList(int id) throws Exception {
        List<Transfer> transfers = new ArrayList<>();
        //data base query to get user info
        String sql = "SELECT transfer_id, amount, transfer_type_desc, transfer_status_desc, t.username " +
                " AS username_from, t1.username AS username_to FROM transfer JOIN account a ON transfer.account_from = a.account_id" +
                " JOIN account a1 ON transfer.account_to = a1.account_id" +
                " JOIN tenmo_user t ON t.user_id = a.user_id" +
                " JOIN tenmo_user t1 ON t1.user_id = a1.user_id" +
                " JOIN transfer_status ts ON ts.transfer_status_id = transfer.transfer_status_id" +
                " JOIN transfer_type tt ON tt.transfer_type_id = transfer.transfer_status_id" +
                " WHERE t.user_id = ?";

        //convert the row into an object.
        SqlRowSet transferList = jdbcTemplate.queryForRowSet(sql, id);
        while (transferList.next()) {
            Transfer transfer = new Transfer();
            //adds the database info into a list
            transfer.setTransferId(transferList.getInt("transfer_id"));
            transfer.setAmount(transferList.getBigDecimal("amount"));
            transfer.setTransferTypeDesc(transferList.getString("transfer_type_desc"));
            transfer.setTransferStatusDesc(transferList.getString("transfer_status_desc"));
            transfer.setUsernameFrom(transferList.getString("username_from"));
            transfer.setUsernameTo(transferList.getString("username_to"));
            //saves object in a list
            transfers.add(transfer);
        }

        return transfers;
    }

    @Override
    public Transfer getTransferById(int transferId) throws Exception {
        String sql = "SELECT transfer_id, amount, transfer_type_desc, transfer_status_desc, t.username " +
                " AS username_from, t1.username AS username_to FROM transfer JOIN account a ON transfer.account_from = a.account_id" +
                " JOIN account a1 ON transfer.account_to = a1.account_id" +
                " JOIN tenmo_user t ON t.user_id = a.user_id" +
                " JOIN tenmo_user t1 ON t1.user_id = a1.user_id" +
                " JOIN transfer_status ts ON ts.transfer_status_id = transfer.transfer_status_id" +
                " JOIN transfer_type tt ON tt.transfer_type_id = transfer.transfer_status_id" +
                " WHERE transfer_id = ?";
        SqlRowSet results = jdbcTemplate.queryForRowSet(sql, transferId);
        Transfer transfer = new Transfer();
        if (results.next()) {

            transfer.setTransferId(results.getInt("transfer_id"));
            transfer.setAmount(results.getBigDecimal("amount"));
            transfer.setTransferTypeDesc(results.getString("transfer_type_desc"));
            transfer.setTransferStatusDesc(results.getString("transfer_status_desc"));
            transfer.setUsernameFrom(results.getString("username_from"));
            transfer.setUsernameTo(results.getString("username_to"));
        }
        return transfer;
    }


    @Override
    public BigDecimal getBalance(int userId) {
        return null;
    }

    @Override
    public int getSetAccountFromId(int accountFromId) {
        return 0;
    }


    @Override
    public boolean notZeroOrNegative() {
        return false;
    }

}


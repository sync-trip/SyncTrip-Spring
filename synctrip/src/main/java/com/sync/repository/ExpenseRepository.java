package com.sync.repository;

import com.sync.domain.expense.Expense;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByBandIdAndIsDeletedFalseOrderByPaidAtDesc(Long bandId);

    Optional<Expense> findByIdAndIsDeletedFalse(Long id);
}
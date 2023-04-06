package net.diaowen.dwsurvey.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.diaowen.common.base.entity.User;
import net.diaowen.common.base.service.AccountManager;
import net.diaowen.dwsurvey.dao.QuestionDao;
import net.diaowen.common.QuType;
import net.diaowen.dwsurvey.service.*;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.diaowen.common.service.BaseServiceImpl;
import net.diaowen.common.utils.ReflectionUtils;
import net.diaowen.dwsurvey.entity.QuCheckbox;
import net.diaowen.dwsurvey.entity.QuMultiFillblank;
import net.diaowen.dwsurvey.entity.QuOrderby;
import net.diaowen.dwsurvey.entity.QuRadio;
import net.diaowen.dwsurvey.entity.QuScore;
import net.diaowen.dwsurvey.entity.Question;
import net.diaowen.dwsurvey.entity.QuestionLogic;
import net.diaowen.dwsurvey.entity.SurveyDirectory;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;


/**
 * 基础题
 * @author keyuan(keyuan258@gmail.com)
 *
 * https://github.com/wkeyuan/DWSurvey
 * http://dwsurvey.net
 */
@Service("questionManager")
public class QuestionManagerImpl extends BaseServiceImpl<Question, String> implements QuestionManager{

	@Autowired
	private QuestionDao questionDao;
	@Autowired
	private QuCheckboxManager quCheckboxManager;
	@Autowired
	private QuRadioManager quRadioManager;
	@Autowired
	private QuMultiFillblankManager quMultiFillblankManager;
	@Autowired
	private QuScoreManager quScoreManager;
	@Autowired
	private QuOrderbyManager quOrderbyManager;
	@Autowired
	private QuestionLogicManager questionLogicManager;
	@Autowired
	private AccountManager accountManager;
	@Autowired
	private SurveyDirectoryManager surveyDirectoryManager;

	@Override
	public void setBaseDao() {
		this.baseDao=questionDao;
	}


	/**
	 * 所有修改，新增题的入口 方法
	 * @param question
	 */
	@Transactional
	@Override
	public void save(Question question){
		User user=accountManager.getCurUser();
		if(user!=null){
			SurveyDirectory survey = surveyDirectoryManager.getSurvey(question.getBelongId());
			if(user.getId().equals(survey.getUserId())){
				String uuid=question.getId();
				if(uuid==null || "".equals(uuid)){
					question.setId(null);
				}
//				question.setUserUuid(user.getId());
				questionDao.save(question);
			}
		}
	}


	/**************************************************************************/
	/**
	 * 依据条件得到符合条件的题列表，不包含选项信息   用于列表显示
	 * @param tag  1题库  2问卷
	 * @return
	 */
	public List<Question> find(String belongId,String tag){
		/*Page<Question> page=new Page<Question>();
		page.setOrderBy("orderById");
		page.setOrderDir("asc");

		List<PropertyFilter> filters=new ArrayList<PropertyFilter>();
		filters.add(new PropertyFilter("EQS_belongId", belongId));
		filters.add(new PropertyFilter("EQI_tag", tag));
		filters.add(new PropertyFilter("NEI_quTag", "3"));
		return findAll(page, filters);*/

		CriteriaBuilder criteriaBuilder=questionDao.getSession().getCriteriaBuilder();
		CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(Question.class);
		Root root = criteriaQuery.from(Question.class);
		criteriaQuery.select(root);
		Predicate eqQuId = criteriaBuilder.equal(root.get("belongId"),belongId);
		Predicate eqTag = criteriaBuilder.equal(root.get("tag"),tag);
		Predicate eqQuTag = criteriaBuilder.notEqual(root.get("quTag"),3);
		criteriaQuery.where(eqQuId,eqTag,eqQuTag);
		criteriaQuery.orderBy(criteriaBuilder.asc(root.get("orderById")));
		return questionDao.findAll(criteriaQuery);

	}

	/**
	 * 查出指定条件下的所有题，及每一题内容的选项   用于展示试卷,如预览,答卷,查看
	 * @param tag
	 * @return
	 */
	public List<Question> findDetails(String belongId,String tag){
		List<Question> questions=find(belongId, tag);
		for (Question question : questions) {
			getQuestionOption(question);
		}
		return questions;
	}
	/**
	 * 得到某一题下面的选项，包含大题下面的小题
	 * @param question
	 */
	public void getQuestionOption(Question question) {
		String quId=question.getId();
		QuType quType=question.getQuType();
		if(quType==QuType.RADIO || quType==QuType.COMPRADIO){
			question.setQuRadios(quRadioManager.findByQuId(quId));
		}else if(quType==QuType.CHECKBOX || quType==QuType.COMPCHECKBOX){
			question.setQuCheckboxs(quCheckboxManager.findByQuId(quId));
		}else if(quType==QuType.MULTIFILLBLANK){
			question.setQuMultiFillblanks(quMultiFillblankManager.findByQuId(quId));
		}else if(quType==QuType.BIGQU){
			//根据大题ID，找出所有小题
			String parentQuId=question.getId();
			List<Question> childQuList=findByParentQuId(parentQuId);
			for (Question childQu : childQuList) {
				getQuestionOption(childQu);
			}
			question.setQuestions(childQuList);
			//根据小题的类型，取选项
		}else if(quType==QuType.SCORE){
		 List<QuScore>	quScores=quScoreManager.findByQuId(quId);
		 question.setQuScores(quScores);
		}else if(quType==QuType.ORDERQU){
			 List<QuOrderby>	quOrderbys=quOrderbyManager.findByQuId(quId);
			 question.setQuOrderbys(quOrderbys);
		}
		List<QuestionLogic> questionLogics=questionLogicManager.findByCkQuId(quId);
		question.setQuestionLogics(questionLogics);
	}

	public List<Question> findByParentQuId(String parentQuId){
		/*List<PropertyFilter> filters=new ArrayList<PropertyFilter>();
		filters.add(new PropertyFilter("EQS_parentQuUuId", parentQuId));
		return findList(filters);*/

		CriteriaBuilder criteriaBuilder=questionDao.getSession().getCriteriaBuilder();
		CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(Question.class);
		Root root = criteriaQuery.from(Question.class);
		criteriaQuery.select(root);
		Predicate eqParentQuId = criteriaBuilder.equal(root.get("parentQuUuId"),parentQuId);
		criteriaQuery.where(eqParentQuId);
		criteriaQuery.orderBy(criteriaBuilder.asc(root.get("orderById")));
		return questionDao.findAll(criteriaQuery);

	}
	/**
	 * 根据ID，得到一批题
	 * @param quIds
	 * @param b  表示是否提出每一题的详细选项信息
	 * @return
	 */
	public List<Question> findByQuIds(String[] quIds, boolean b) {
		List<Question> questions=new ArrayList<Question>();
		if(quIds==null || quIds.length<=0){
			return questions;
		}
		StringBuffer hqlBuf=new StringBuffer("from Question qu where qu.id in(");
		for (String quId : quIds) {
			hqlBuf.append("'"+quId+"'").append(",");
		}
//		hqlBuf.append("0)");
		String hql=hqlBuf.substring(0, hqlBuf.lastIndexOf(","))+")";
		questions=questionDao.find(hql);
		if(b){
			for (Question question : questions) {
				getQuestionOption(question);
			}
		}
		return questions;
	}

	/**
	 * 批量删除题，及题包含的选项一同删除-真删除。
	 * @param delQuIds
	 */
	@Transactional
	public void deletes(String[] delQuIds) {
		if(delQuIds!=null){
			for (String quId : delQuIds) {

			}
		}
	}

	@Transactional
	public void delete(String quId){
		if(quId!=null && !"".equals(quId)){
			Question question=get(quId);
			//同时删除掉相应的选项
			if(question!=null){
				QuType quType=question.getQuType();
				String belongId = question.getBelongId();
				int orderById= question.getOrderById();
				questionDao.delete(question);
				//更新ID
				questionDao.quOrderByIdDel1(belongId,orderById);
			}
		}
	}

	/**
	 * 题排序
	 * @param prevId
	 * @param nextId
	 */
	@Transactional
	public boolean upsort(String prevId, String nextId) {
		if(prevId!=null && !"".equals(prevId) && nextId!=null && !"".equals(nextId)){
			Question prevQuestion=get(prevId);
			Question nextQuestion=get(nextId);
			int prevNum=prevQuestion.getOrderById();
			int nextNum=nextQuestion.getOrderById();

			prevQuestion.setOrderById(nextNum);
			nextQuestion.setOrderById(prevNum);

			save(prevQuestion);
			save(nextQuestion);
			return true;
		}
		return false;
	}

	public Question findUnById(String id){
		return questionDao.findUniqueBy("id", id);
	}

	public List<Question> findByparentQuId(String parentQuUuId){
		/*List<PropertyFilter> filters=new ArrayList<PropertyFilter>();
		filters.add(new PropertyFilter("EQS_parentQuUuId", parentQuUuId));
		return findList(filters);*/
		CriteriaBuilder criteriaBuilder=questionDao.getSession().getCriteriaBuilder();
		CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(Question.class);
		Root root = criteriaQuery.from(Question.class);
		criteriaQuery.select(root);
		Predicate eqParentQuId = criteriaBuilder.equal(root.get("parentQuUuId"),parentQuUuId);
		criteriaQuery.where(eqParentQuId);
		criteriaQuery.orderBy(criteriaBuilder.asc(root.get("orderById")));
		return questionDao.findAll(criteriaQuery);
	}

	public void saveBySurvey(String belongId  ,int tag, List<Question> questions) {
		for (Question question : questions) {
			copyQu(belongId, tag, question);
		}
	}
	/**
	 * 保存选中的题目 即从题库或从其它试卷中的题
	 */
	@Transactional
	public void saveChangeQu(String belongId,int tag, String[] quIds) {
		for (String quId : quIds) {
			Question changeQuestion=findUnById(quId);
			copyQu(belongId, tag, changeQuestion);
		}
	}

	private void copyQu(String belongId, int tag, Question changeQuestion) {
		String quId=changeQuestion.getId();
		if(changeQuestion.getQuType()==QuType.BIGQU){
			Question question=new Question();
			ReflectionUtils.copyAttr(changeQuestion,question);
			//设置相关要改变的值
			question.setId(null);
			question.setBelongId(belongId);
			question.setCreateDate(new Date());
			question.setTag(tag);
			question.setQuTag(2);
			question.setCopyFromId(quId);

			List<Question> changeChildQuestions=findByparentQuId(quId);
			List<Question> qulits=new ArrayList<Question>();
			for (Question changeQu : changeChildQuestions) {
				Question question2=new Question();
				ReflectionUtils.copyAttr(changeQu,question2);
				//设置相关要改变的值
				question2.setId(null);
				question2.setBelongId(belongId);
				question2.setCreateDate(new Date());
				question2.setTag(tag);
				question2.setQuTag(3);
				question2.setCopyFromId(changeQu.getId());

				getQuestionOption(changeQu);
				copyItems(belongId,changeQu, question2);

				qulits.add(question2);
			}
			question.setQuestions(qulits);
			save(question);
		}else{
			copyroot(belongId, tag, changeQuestion);
		}
	}
	private void copyroot(String belongId,Integer tag, Question changeQuestion) {
		//拷贝先中的问题属性值到新对象中
		Question question=new Question();
		ReflectionUtils.copyAttr(changeQuestion,question);
		//设置相关要改变的值
		question.setId(null);
		question.setBelongId(belongId);
		question.setCreateDate(new Date());
		question.setTag(tag);
		question.setCopyFromId(changeQuestion.getId());

		getQuestionOption(changeQuestion);
		copyItems(belongId,changeQuestion, question);
		save(question);
	}
	private void copyItems(String quBankUuid,Question changeQuestion, Question question) {
		QuType quType=changeQuestion.getQuType();
		if(quType==QuType.RADIO || quType==QuType.COMPRADIO){
			List<QuRadio> changeQuRadios=changeQuestion.getQuRadios();
			List<QuRadio> quRadios=new ArrayList<QuRadio>();
			for (QuRadio changeQuRadio : changeQuRadios) {
				QuRadio quRadio=new QuRadio();
				ReflectionUtils.copyAttr(changeQuRadio,quRadio);
				quRadio.setId(null);
				quRadios.add(quRadio);
			}
			question.setQuRadios(quRadios);
		}else if(quType==QuType.CHECKBOX || quType==QuType.COMPCHECKBOX){
			List<QuCheckbox> changeQuCheckboxs=changeQuestion.getQuCheckboxs();
			List<QuCheckbox> quCheckboxs=new ArrayList<QuCheckbox>();
			for (QuCheckbox changeQuCheckbox : changeQuCheckboxs) {
				QuCheckbox quCheckbox=new QuCheckbox();
				ReflectionUtils.copyAttr(changeQuCheckbox,quCheckbox);
				quCheckbox.setId(null);
				quCheckboxs.add(quCheckbox);
			}
			question.setQuCheckboxs(quCheckboxs);
		}else if(quType==QuType.MULTIFILLBLANK){
			List<QuMultiFillblank> changeQuDFillbanks=changeQuestion.getQuMultiFillblanks();
			List<QuMultiFillblank> quDFillbanks=new ArrayList<QuMultiFillblank>();
			for (QuMultiFillblank changeQuDFillbank : changeQuDFillbanks) {
				QuMultiFillblank quDFillbank=new QuMultiFillblank();
				ReflectionUtils.copyAttr(changeQuDFillbank,quDFillbank);
				quDFillbank.setId(null);
				quDFillbanks.add(quDFillbank);
			}
			question.setQuMultiFillblanks(quDFillbanks);
		}else if(quType==QuType.SCORE){
			//评分
			List<QuScore> changeQuScores=changeQuestion.getQuScores();
			List<QuScore> quScores=new ArrayList<QuScore>();
			for (QuScore changeQuScore : changeQuScores) {
				QuScore quScore=new QuScore();
				ReflectionUtils.copyAttr(changeQuScore, quScore);
				quScore.setId(null);
				quScores.add(quScore);
			}
			question.setQuScores(quScores);
		}else if(quType==QuType.ORDERQU){
			//评分
			List<QuOrderby> changeQuOrderbys=changeQuestion.getQuOrderbys();
			List<QuOrderby> quOrderbyList=new ArrayList<QuOrderby>();
			for (QuOrderby changeQuOrder : changeQuOrderbys) {
				QuOrderby quOrderby=new QuOrderby();
				ReflectionUtils.copyAttr(changeQuOrder, quOrderby);
				quOrderby.setId(null);
				quOrderbyList.add(quOrderby);
			}
			question.setQuOrderbys(quOrderbyList);
		}

	}


	@Override
	public List<Question> findStatsRowVarQus(SurveyDirectory survey) {
		Criterion criterion1=Restrictions.eq("belongId", survey.getId());
		Criterion criterion2=Restrictions.eq("tag", 2);

//		Criterion criterion31=Restrictions.ne("quType", QuType.FILLBLANK);
//		Criterion criterion32=Restrictions.ne("quType", QuType.MULTIFILLBLANK);
//		Criterion criterion33=Restrictions.ne("quType", QuType.ANSWER);
//
////		Criterion criterion3=Restrictions.or(criterion31, criterion32);
//		//where s=2 and (fds !=1 or fds!=2 )
//		return questionDao.find(criterion1,criterion2,criterion31,criterion32,criterion33);

		Criterion criterion31=Restrictions.ne("quType", QuType.FILLBLANK);
		Criterion criterion32=Restrictions.ne("quType", QuType.MULTIFILLBLANK);
		Criterion criterion33=Restrictions.ne("quType", QuType.ANSWER);
		Criterion criterion34=Restrictions.ne("quType", QuType.CHENCHECKBOX);
		Criterion criterion35=Restrictions.ne("quType", QuType.CHENFBK);
		Criterion criterion36=Restrictions.ne("quType", QuType.CHENRADIO);
		Criterion criterion37=Restrictions.ne("quType", QuType.ENUMQU);
		Criterion criterion38=Restrictions.ne("quType", QuType.ORDERQU);
		Criterion criterion39=Restrictions.ne("quType", QuType.SCORE);

		return questionDao.find(criterion1,criterion2,criterion31,criterion32,criterion33,criterion34,criterion35,criterion36,criterion37,criterion38,criterion39);
//		return null;
	}


	@Override
	public List<Question> findStatsColVarQus(SurveyDirectory survey) {
		Criterion criterion1=Restrictions.eq("belongId", survey.getId());
		Criterion criterion2=Restrictions.eq("tag", 2);

//		Criterion criterion31=Restrictions.ne("quType", QuType.FILLBLANK);
//		Criterion criterion32=Restrictions.ne("quType", QuType.MULTIFILLBLANK);
//		Criterion criterion33=Restrictions.ne("quType", QuType.ANSWER);
//
////		Criterion criterion3=Restrictions.or(criterion31, criterion32);
//		//where s=2 and (fds !=1 or fds!=2 )
//		return questionDao.find(criterion1,criterion2,criterion31,criterion32,criterion33);

		Criterion criterion31=Restrictions.ne("quType", QuType.FILLBLANK);
		Criterion criterion32=Restrictions.ne("quType", QuType.MULTIFILLBLANK);
		Criterion criterion33=Restrictions.ne("quType", QuType.ANSWER);
		Criterion criterion34=Restrictions.ne("quType", QuType.CHENCHECKBOX);
		Criterion criterion35=Restrictions.ne("quType", QuType.CHENFBK);
		Criterion criterion36=Restrictions.ne("quType", QuType.CHENRADIO);
		Criterion criterion37=Restrictions.ne("quType", QuType.ENUMQU);
		Criterion criterion38=Restrictions.ne("quType", QuType.ORDERQU);
		Criterion criterion39=Restrictions.ne("quType", QuType.SCORE);

		return questionDao.find(criterion1,criterion2,criterion31,criterion32,criterion33,criterion34,criterion35,criterion36,criterion37,criterion38,criterion39);
	}


	@Override
	public Question getDetail(String quId) {
		Question question=get(quId);
		getQuestionOption(question);
		return question;
	}

	@Transactional
	public void update(Question entity){
		questionDao.update(entity);
	}

}
